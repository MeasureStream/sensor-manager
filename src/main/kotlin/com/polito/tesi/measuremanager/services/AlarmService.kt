package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.LoraUplink
import com.polito.tesi.measuremanager.entities.*
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.repositories.SensorAlarmRepository
import com.polito.tesi.measuremanager.template.*
import java.time.OffsetDateTime
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Allarmi 0xA0 ed eventi 0xA2: dal payload alla riga di database, con i testi risolti.
 *
 * I due canali restano distinti perche' sono cose diverse. Un allarme e' una condizione
 * della grandezza misurata: si attiva, resta latchato, rientra. Un evento e' un fatto
 * accaduto al dispositivo: ha un istante e non ha un rientro. Trattarli insieme obbligherebbe
 * a inventare un rientro per gli eventi o a perdere il latch degli allarmi.
 */
@Service
class AlarmService(
        private val cur: ControlUnitRepository,
        private val alarms: SensorAlarmRepository,
        private val events: DeviceEventRepository,
        private val decoder: AlarmDecoder,
        private val templateService: TemplateService,
        private val muModelService: MuModelService,
        private val conversion: SensorConversion,
) {
    private val log = LoggerFactory.getLogger(AlarmService::class.java)

    @Transactional
    fun onAlarms(dto: LoraUplink) {
        val cu = cur.findByDevEui(dto.devEui) ?: return unknownCu(dto)
        val bytes = decodePayload(dto) ?: return

        val message =
                try {
                    decoder.decodeAlarms(bytes) { localId, sensorIndex ->
                        sensorAt(cu, localId, sensorIndex)?.let {
                            templateService.getTemplate(it.modelName)
                        }
                    }
                } catch (e: AlarmPayloadError) {
                    log.warn("Allarme non leggibile da DevEUI={}: {}", dto.devEui, e.message)
                    return
                }

        reportGap("allarmi", cu.lastAlarmSeq, message.alarmSeq, dto.devEui)
        cu.lastAlarmSeq = message.alarmSeq

        val received = timestampOf(dto)
        message.entries.forEach { entry ->
            val sensor = sensorAt(cu, entry.localId, entry.sensorIndex)
            // Il valore arriva nella stessa forma dei report: grezzo oggi, quindi passa dalla
            // stessa conversione delle misure e non da una copia della formula.
            val converted =
                    sensor?.let {
                        conversion.toPhysical(templateService.getTemplate(it.modelName), entry.rawValue.toDouble())
                    }

            alarms.save(
                    SensorAlarm(
                            controlUnit = cu,
                            sensor = sensor,
                            receivedAt = received,
                            alarmSeq = message.alarmSeq,
                            localId = entry.localId,
                            sensorIndex = entry.sensorIndex,
                            cleared = entry.cleared,
                            alarmWord = entry.alarmWord,
                            conditions = entry.conditions,
                            rawValue = entry.rawValue,
                            value = converted?.value,
                    )
            )

            log.info(
                    "Allarme da CU {} MU {} slot {}: {} ({})",
                    cu.name,
                    entry.localId,
                    entry.sensorIndex,
                    entry.conditions.joinToString { it["text"].toString() },
                    if (entry.cleared) "rientro" else "attivazione",
            )
        }
        cur.save(cu)
    }

    @Transactional
    fun onEvents(dto: LoraUplink) {
        val cu = cur.findByDevEui(dto.devEui) ?: return unknownCu(dto)
        val bytes = decodePayload(dto) ?: return

        val message =
                try {
                    decoder.decodeEvents(bytes) { source, code -> constructorEvent(cu, source, code) }
                } catch (e: AlarmPayloadError) {
                    log.warn("Eventi non leggibili da DevEUI={}: {}", dto.devEui, e.message)
                    return
                }

        reportGap("eventi", cu.lastEventSeq, message.eventSeq, dto.devEui)
        cu.lastEventSeq = message.eventSeq

        val received = timestampOf(dto)
        message.entries.forEach { entry ->
            // L'evento porta un'eta', non un istante: l'istante lo ricava il server, e si
            // porta dietro l'incertezza della fascia di codifica.
            val occurred = entry.elapsed?.let { received.minusSeconds(it.seconds) }

            events.save(
                    DeviceEvent(
                            controlUnit = cu,
                            sensor =
                                    if (entry.sensorIndex == 0xFF) null
                                    else sensorAt(cu, entry.source, entry.sensorIndex),
                            receivedAt = received,
                            eventSeq = message.eventSeq,
                            source = entry.source,
                            sensorIndex = entry.sensorIndex,
                            code = entry.code,
                            param = entry.param,
                            ageRaw = entry.ageRaw,
                            occurredAt = occurred,
                            ageUncertaintySeconds = entry.elapsed?.uncertaintySeconds,
                            description = entry.description,
                    )
            )

            log.info("Evento da CU {}: {}", cu.name, entry.description)
        }
        cur.save(cu)
    }

    /* ------------------------------------------------------------------ interno */

    /** Il sensore all'indirizzo (LID della MU, indice nel modello). */
    private fun sensorAt(cu: ControlUnit, localId: Int, sensorIndex: Int): Sensor? =
            cu.measurementUnits
                    .firstOrNull { it.localId == localId }
                    ?.sensors
                    ?.firstOrNull { it.sensorIndex == sensorIndex }

    /**
     * I codici da 0x80 in su appartengono al costruttore e sono univoci dentro la coppia
     * `(templateId, MAJOR)` del modello di MU: si risolvono da li', non da una tabella nel
     * codice.
     */
    private fun constructorEvent(cu: ControlUnit, source: Int, code: Int): String? {
        if (code < 0x80) return null
        val model = cu.measurementUnits.firstOrNull { it.localId == source }?.model ?: return null

        @Suppress("UNCHECKED_CAST")
        val declared =
                muModelService.resolveModel(model)?.properties?.get("eventCodes") as? List<Map<String, Any?>>
        val entry = declared?.firstOrNull { (it["code"] as? Number)?.toInt() == code }
        return entry?.get("description")?.toString()
    }

    private fun decodePayload(dto: LoraUplink): ByteArray? =
            try {
                Base64.getDecoder().decode(dto.rawPayload)
            } catch (e: Exception) {
                log.warn("Payload non in base64 da DevEUI={}: {}", dto.devEui, e.message)
                null
            }

    private fun timestampOf(dto: LoraUplink): OffsetDateTime =
            dto.timestamp?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                    ?: OffsetDateTime.now()

    /**
     * ALARM_SEQ ed EVENT_SEQ sono progressivi: un salto significa che per strada se n'e'
     * perso qualcuno, ed e' l'unico modo che il server ha di accorgersene.
     */
    private fun reportGap(what: String, last: Int?, current: Int, devEui: Long) {
        if (last == null) return
        val expected = (last + 1) and 0xFF
        if (current != expected) {
            log.warn(
                    "DevEUI={}: sequenza {} discontinua, atteso {} ricevuto {}: messaggi persi",
                    devEui,
                    what,
                    expected,
                    current,
            )
        }
    }

    private fun unknownCu(dto: LoraUplink) {
        log.warn("Control Unit non trovata per DevEUI={}: messaggio ignorato", dto.devEui)
    }
}
