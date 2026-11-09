package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.template.ProtocolService
import java.time.OffsetDateTime

/**
 * Una MU vista da fuori dalla sua CU: la riga della pagina «Mie MU».
 *
 * Non e' il [MeasurementUnitDTO] con qualche campo in piu'. Quello porta i sensori e, con
 * loro, un riferimento di template ciascuno: su una tabella da cinquanta righe sono
 * cinquanta volte piu' dati di quelli che la tabella mostra. Qui c'e' solo cio' che si
 * legge nelle colonne, e il resto lo si chiede aprendo il dettaglio.
 */
data class MeasurementUnitSummaryDTO(
        val id: Long,
        val extendedId: Long,
        /** `MU hA1B2`: le ultime due parole dell'EUI, come la MU si chiama ovunque. */
        val label: String,
        val model: Int,
        /** MAJOR del modello dichiarato nella 0x11; null se e' arrivata la vecchia 0x10. */
        val modelMajor: Int?,
        /** Posizione sul bus della CU attuale. Non e' un'identita': cambia con il montaggio. */
        val localId: Int,
        val sensorCount: Int,
        /** Montata adesso su una CU. Falso per una MU in un cassetto. */
        val attached: Boolean,
        /** L'ultima CU su cui e' stata montata, che sia ancora la sua o no. */
        val controlUnit: ControlUnitRefDTO?,
        val detachedAt: OffsetDateTime?,
        val lastSeen: OffsetDateTime?,
        /** Montata **e** vista di recente: la distinzione fra «offline» e «staccata». */
        val online: Boolean,
        val archived: Boolean,
        /** I bit di stato dell'ultima 0x12, gia' tradotti dal dizionario di protocollo. */
        val statusFlags: List<StatusFlagDTO>,
        val openEvents: Long,
        val openAlarms: Long,
)

/** Quel tanto di CU che serve a nominarla e ad aprirne la scheda. */
data class ControlUnitRefDTO(val id: Long, val name: String?, val deviceId: String?)

/**
 * Soglia minima per dire «vista di recente», quando il periodo di trasmissione non si sa.
 * E' la stessa che usa la CU, e per lo stesso motivo: sotto la mezz'ora un silenzio puo'
 * essere un ritardo della rete invece che un guasto.
 */
private const val MU_OFFLINE_THRESHOLD_MINUTES = 30L

/**
 * `name` e `deviceId` della CU sono `lateinit` su colonne che ammettono il nullo: su una riga
 * nata storta il semplice accesso lancia. In una scheda di dettaglio e' giusto che si veda;
 * in un elenco significherebbe perdere tutte le righe per colpa di una.
 */
private fun ControlUnit.ifSet(read: ControlUnit.() -> String): String? =
        runCatching { read() }.getOrNull()

fun ControlUnit.toRefDTO() = ControlUnitRefDTO(id = id, name = ifSet { name }, deviceId = ifSet { deviceId })

/** `MU hA1B2`, la stessa etichetta che compare nelle schede dei sensori. */
fun MeasurementUnit.label(): String = "MU h%04X".format(extendedId and 0xFFFF)

/**
 * In uso vuol dire due cose insieme: montata su una CU, e vista entro due periodi di
 * trasmissione di quella CU. Senza il primo pezzo una MU in un cassetto resterebbe «attiva»
 * per mezz'ora dopo lo stacco; senza il secondo una MU che ha smesso di rispondere sul bus
 * sembrerebbe viva finche' la sua CU lo e'.
 */
fun MeasurementUnit.isOnline(protocol: ProtocolService?): Boolean {
    val cu = controlUnit ?: return false
    val seen = lastSeen ?: return false
    val intervalMinutes = protocol?.transmissionPeriodMinutes(cu.transmissionInterval) ?: 0L
    val thresholdMinutes = maxOf(MU_OFFLINE_THRESHOLD_MINUTES, intervalMinutes * 2)
    return seen.isAfter(OffsetDateTime.now().minusMinutes(thresholdMinutes))
}

fun MeasurementUnit.toSummaryDTO(
        protocol: ProtocolService?,
        openEvents: Long = 0,
        openAlarms: Long = 0,
) =
        MeasurementUnitSummaryDTO(
                id = id,
                extendedId = extendedId,
                label = label(),
                model = model,
                modelMajor = modelMajor,
                localId = localId,
                sensorCount = sensors.size,
                attached = controlUnit != null,
                // L'ultima CU vale anche quando il legame attivo c'e': sono la stessa, e
                // cosi' la colonna ha sempre qualcosa da dire.
                controlUnit = (controlUnit ?: lastControlUnit)?.toRefDTO(),
                detachedAt = detachedAt,
                lastSeen = lastSeen,
                online = isOnline(protocol),
                archived = archivedAt != null,
                statusFlags = protocol?.muStatusFlags(statusWord)?.map { it.toDTO() } ?: emptyList(),
                openEvents = openEvents,
                openAlarms = openAlarms,
        )
