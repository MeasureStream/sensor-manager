package com.polito.tesi.measuremanager.sensorconfig

import com.polito.tesi.measuremanager.dtos.MAX_SENSORS_PER_CU
import com.polito.tesi.measuremanager.dtos.ProcessingCommandDTO
import com.polito.tesi.measuremanager.dtos.SamplingCommandDTO
import com.polito.tesi.measuremanager.dtos.SensorConfigCommandSetDTO
import com.polito.tesi.measuremanager.dtos.SensorSelectorDTO
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.StatBitmap
import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Da comandi raggruppati a stato desiderato, sensore per sensore.
 *
 * L'interfaccia ragiona per **gruppi**: «questi otto sensori a un minuto», con il selettore
 * piu' economico possibile. Il protocollo v1.2 ragiona per **record**: la 0x21 elenca il
 * periodo di ogni sensore in ordine, la 0x23 indirizza un sensore alla volta con il suo
 * indice globale. Qui si passa dal primo al secondo.
 *
 * Due cose rendono questo passaggio meno banale di quanto sembri.
 *
 * **La 0x21 e' un comando di stato pieno, non un delta.** Porta il periodo di *tutti* i
 * sensori: se si inviasse solo cio' che e' cambiato, ogni configurazione spegnerebbe tutto il
 * resto. Quindi si parte dallo stato che il database gia' conosce e ci si applicano sopra i
 * comandi ricevuti.
 *
 * **Quello che il protocollo non sa portare non si finge portato.** Il percentile, l'allarme
 * sul tempo fuori scala, l'esposizione cumulata e i valori di soglia in grandezza fisica non
 * hanno oggi un campo in cui viaggiare. Il pianificatore li raccoglie negli avvisi, e la
 * risposta li mostra: e' l'unico modo perche' chi configura sappia cosa e' arrivato davvero.
 */
@Component
class SensorConfigPlanner(private val protocol: ProtocolService) {

    private val log = LoggerFactory.getLogger(SensorConfigPlanner::class.java)

    fun plan(cu: ControlUnit, command: SensorConfigCommandSetDTO): SensorConfigPlan {
        val warnings = mutableListOf<String>()

        val desired =
                canonicalOrder(cu).map {
                    DesiredSensorState(
                            target = it,
                            // samplingF conserva l'indice di scala, non i secondi: il nome e'
                            // ereditato, il contenuto e' quello che viaggia sul filo.
                            periodIndex = it.sensor.samplingF.toInt(),
                            measure = it.sensor.configurationMeasure,
                    )
                }

        if (desired.isEmpty()) {
            warnings += "La CU non ha nessun sensore censito: configurare non ha effetto finche' non arriva una notifica di topologia."
        }

        command.sampling.forEach { applySampling(it, desired, warnings) }
        command.processing.forEach { applyProcessing(it, desired, warnings) }
        collectThresholdWarnings(command, warnings)

        return SensorConfigPlan(sensors = desired, warnings = warnings)
    }

    /* ------------------------------------------------------------------ *
     * L'ordine canonico
     * ------------------------------------------------------------------ */

    /**
     * MU per Local ID crescente, slot per indice crescente, numerati da zero senza salti.
     *
     * I sensori oltre il limite della CU restano nell'elenco — occupano comunque la loro
     * posizione nello stream della 0x21 — ma nascono non configurabili, quindi nessun comando
     * li tocca e il loro periodo resta quello che era.
     */
    private fun canonicalOrder(cu: ControlUnit): List<ConfigurableSensor> {
        var globalIndex = 0
        return cu.measurementUnits.sortedBy { it.localId }.flatMap { mu ->
            mu.sensors.sortedBy { it.sensorIndex }.map { sensor ->
                val position = globalIndex++
                ConfigurableSensor(
                        globalIndex = position,
                        muLocalId = mu.localId,
                        sensorIndex = sensor.sensorIndex,
                        sensor = sensor,
                        configurable = position < MAX_SENSORS_PER_CU,
                )
            }
        }
    }

    /* ------------------------------------------------------------------ *
     * Applicazione dei comandi
     * ------------------------------------------------------------------ */

    private fun applySampling(
            command: SamplingCommandDTO,
            desired: List<DesiredSensorState>,
            warnings: MutableList<String>,
    ) {
        if (!isValidSamplingIndex(command.periodIndex)) {
            warnings +=
                    "Periodo di campionamento ${command.periodIndex} fuori dalla scala del protocollo: comando ignorato."
            return
        }

        targets(command.selector, desired, warnings).forEach { state ->
            if (state.periodIndex == command.periodIndex) return@forEach
            state.periodIndex = command.periodIndex
            state.periodChanged = true
        }
    }

    private fun applyProcessing(
            command: ProcessingCommandDTO,
            desired: List<DesiredSensorState>,
            warnings: MutableList<String>,
    ) {
        val canonical = StatBitmap.canonicalMeasure(command.measure)
        if (canonical == null) {
            warnings +=
                    "La misura «${command.measure}» non ha ancora un posto nel record della 0x23: " +
                            "l'elaborazione di quei sensori resta quella di prima."
            return
        }

        targets(command.selector, desired, warnings).forEach { state ->
            // Il confronto e' sulla bitmap, non sulla stringa: nomi diversi che accendono gli
            // stessi bit chiedono alla MU la stessa cosa, e rimandarli costerebbe un downlink
            // e un incremento di CFG_VER per niente.
            if (StatBitmap.of(state.measure) == StatBitmap.of(canonical)) return@forEach
            state.measure = canonical
            state.measureChanged = true
        }

        val alarms = command.alarms
        if (alarms != null && (alarms.timeOutOfRange || alarms.cumulativeExposure)) {
            warnings +=
                    "Tempo fuori scala ed esposizione cumulata non hanno un bit nel comando 0x23: non sono stati armati."
        }
    }

    /**
     * Le soglie non partono, e il motivo va detto per intero.
     *
     * Sul filo viaggiano **grezze**, cioe' nel conteggio del convertitore; l'interfaccia le
     * raccoglie in grandezza fisica. Per tradurle servirebbe invertire la formula di taratura
     * del template, che non e' invertibile in generale: e' il lavoro registrato come rimandato
     * con cognizione in `STATO_LAVORI.md`. Spedire un numero fisico dentro un campo grezzo
     * farebbe scattare allarmi a soglie che nessuno ha scelto.
     */
    private fun collectThresholdWarnings(
            command: SensorConfigCommandSetDTO,
            warnings: MutableList<String>,
    ) {
        val valued = command.thresholds.count { it.fields.isNotEmpty() }
        if (valued == 0) return
        warnings +=
                "Soglie conservate ma non trasmesse ($valued ${if (valued == 1) "gruppo" else "gruppi"}): " +
                        "viaggiano in valore grezzo e manca l'inversione della taratura. Nessun allarme e' stato armato."
    }

    /* ------------------------------------------------------------------ *
     * Il selettore
     * ------------------------------------------------------------------ */

    /**
     * Espande un selettore nei sensori che tocca davvero.
     *
     * Un destinatario che non esiste non e' un errore fatale — la topologia puo' essere
     * cambiata fra l'apertura della pagina e il salvataggio — ma non deve nemmeno sparire in
     * silenzio: diventa un avviso, e il resto del comando viene applicato.
     */
    private fun targets(
            selector: SensorSelectorDTO,
            desired: List<DesiredSensorState>,
            warnings: MutableList<String>,
    ): List<DesiredSensorState> {
        val configurable = desired.filter { it.target.configurable }

        val resolved =
                when (selector.kind.lowercase()) {
                    "broadcast" -> configurable
                    "mu" -> byMeasurementUnit(selector, configurable, warnings)
                    "list" -> byList(selector, desired, warnings)
                    "bitmap" -> byBitmap(selector, desired, warnings)
                    else -> {
                        warnings += "Selettore «${selector.kind}» sconosciuto: comando ignorato."
                        emptyList()
                    }
                }

        // Un comando che arriva a un sensore non configurabile non si applica a meta': si
        // scarta quel destinatario e lo si dice.
        val (ok, blocked) = resolved.partition { it.target.configurable }
        blocked.forEach {
            warnings +=
                    "${it.target.label} e' oltre il limite di $MAX_SENSORS_PER_CU sensori per CU: non e' configurabile."
        }
        return ok
    }

    private fun byMeasurementUnit(
            selector: SensorSelectorDTO,
            configurable: List<DesiredSensorState>,
            warnings: MutableList<String>,
    ): List<DesiredSensorState> {
        val localId = selector.muLocalId
        if (localId == null) {
            warnings += "Selettore per MU senza Local ID: comando ignorato."
            return emptyList()
        }
        val hits = configurable.filter { it.target.muLocalId == localId }
        if (hits.isEmpty()) warnings += "Nessun sensore configurabile sulla MU $localId: comando ignorato."
        return hits
    }

    private fun byList(
            selector: SensorSelectorDTO,
            desired: List<DesiredSensorState>,
            warnings: MutableList<String>,
    ): List<DesiredSensorState> {
        if (selector.sensors.isEmpty()) {
            warnings += "Selettore a lista vuoto: comando ignorato."
            return emptyList()
        }
        return selector.sensors.mapNotNull { ref ->
            val hit =
                    desired.firstOrNull {
                        it.target.muLocalId == ref.muLocalId && it.target.sensorIndex == ref.sensorIndex
                    }
            if (hit == null) {
                warnings += "MU ${ref.muLocalId}, slot ${ref.sensorIndex}: sensore non censito, saltato."
            }
            hit
        }
    }

    /**
     * Bitmap a 48 bit: il bit meno significativo e' il primo sensore dell'ordine canonico.
     * Si legge con [BigInteger] e non con un `Long` perche' quarantotto bit ci stanno, ma la
     * stringa arriva dall'esterno e un giorno la mappa potrebbe allargarsi.
     */
    private fun byBitmap(
            selector: SensorSelectorDTO,
            desired: List<DesiredSensorState>,
            warnings: MutableList<String>,
    ): List<DesiredSensorState> {
        val hex = selector.bitmapHex
        if (hex.isNullOrBlank()) {
            warnings += "Selettore a bitmap senza valore: comando ignorato."
            return emptyList()
        }

        val bits =
                try {
                    BigInteger(hex.trim(), 16)
                } catch (e: NumberFormatException) {
                    log.debug("Bitmap non esadecimale: {}", hex)
                    warnings += "Bitmap «$hex» non leggibile: comando ignorato."
                    return emptyList()
                }

        if (bits.bitLength() > desired.size) {
            warnings +=
                    "La bitmap seleziona posizioni oltre il ${desired.size}° sensore: quei bit sono stati ignorati."
        }

        return desired.indices.filter { bits.testBit(it) }.map { desired[it] }
    }

    /* ------------------------------------------------------------------ *
     * Validazione
     * ------------------------------------------------------------------ */

    /**
     * Un indice vale se la scala del dizionario sa dire a quanti secondi corrisponde. Senza
     * dizionario non si inventa un limite: si accetta qualunque byte e sara' la CU a rifiutare.
     */
    private fun isValidSamplingIndex(index: Int): Boolean {
        if (index < 0 || index > 0xFF) return false
        val scale = protocol.dictionary()?.sampling ?: return true
        if (index == scale.off) return true
        return scale.seconds(index) != null
    }
}
