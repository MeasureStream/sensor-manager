package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.template.ProtocolService
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Una CU vista come parte di una flotta: la riga della pagina «Mie CU».
 *
 * Non e' il [ControlUnitDTO] con qualche campo in meno. Quello porta dentro di se' tutte le
 * MU, e ogni MU i suoi sensori con un riferimento di template ciascuno: per una tabella che
 * della topologia mostra due numeri sono centinaia di volte i dati che servono. Qui ci sono
 * le colonne e nient'altro, e chi vuole il resto apre la scheda.
 *
 * Porta anche le due cose che il DTO completo non sa dire: quanti allarmi e quanti avvisi
 * restano aperti. La pagina dei dispositivi li chiedeva con una richiesta per CU — dieci
 * dispositivi, dieci richieste — e gli avvisi non li contava affatto.
 */
data class ControlUnitSummaryDTO(
        val id: Long,
        /** L'EUI in decimale, come nel [ControlUnitDTO]: a formattarlo pensa chi lo mostra. */
        val devEui: String,
        val deviceId: String?,
        val name: String?,
        val model: Int,
        val semanticLocation: String,
        /** ProtoVer del poll: 18 (0x12) per il protocollo v1.2, null per i firmware precedenti. */
        val protocolVer: Int?,
        val measurementUnitCount: Int,
        /** Sensori censiti su tutte le MU. Il tetto configurabile per CU e' 48. */
        val sensorCount: Int,
        val online: Boolean,
        /**
         * Ultimo contatto, con l'offset scritto.
         *
         * Sulla riga e' un `LocalDateTime` in UTC, e il [ControlUnitDTO] lo passa cosi' com'e':
         * un istante senza fuso, che il browser legge come ora locale e mostra spostato di
         * un'ora o due. Qui l'offset si dichiara, e chi legge non deve indovinarlo.
         */
        val lastSeen: OffsetDateTime?,
        val remainingBattery: Double,
        val acPowered: Boolean,
        val isCharging: Boolean,
        val rssi: Double,
        /** I bit alzati nell'ultimo poll, gia' tradotti dal dizionario di protocollo. */
        val statusFlags: List<StatusFlagDTO>,
        val openAlarms: Long,
        val openEvents: Long,
        /** Report scartati per CFG_VER diverso: ogni unita' e' una misura che non c'e' piu'. */
        val configMismatchCount: Long,
        /** Report scartati perche' illeggibili: payload troncato o corrotto. */
        val decodeFailureCount: Long,
        /** CFG_VER come viaggia sul filo, da confrontare con quello che la CU dichiara. */
        val configVersionOnWire: Int,
        val lastReportedConfigVersion: Int?,
        val cmdSeq: Int,
        val appliedCmdSeq: Int?,
)

/**
 * I conteggi arrivano da fuori perche' `measurementUnits` e `sensors` sono collezioni pigre:
 * leggerne la dimensione riga per riga sarebbe una query per CU piu' una per MU, cioe' il
 * modo classico di rendere lenta una pagina appena la flotta cresce.
 */
fun ControlUnit.toSummaryDTO(
        protocol: ProtocolService?,
        measurementUnitCount: Int,
        sensorCount: Int,
        openAlarms: Long = 0,
        openEvents: Long = 0,
) =
        ControlUnitSummaryDTO(
                id = id,
                devEui = devEui.toString(),
                // `name` e `deviceId` sono lateinit su colonne che ammettono il nullo: in un
                // elenco una riga nata storta non deve portarsi via tutte le altre.
                deviceId = ifSet { deviceId },
                name = ifSet { name },
                model = model,
                semanticLocation = semanticLocation,
                protocolVer = protocolVer,
                measurementUnitCount = measurementUnitCount,
                sensorCount = sensorCount,
                online = isOnline(protocol),
                lastSeen = lastSeen?.atOffset(ZoneOffset.UTC),
                remainingBattery = remainingBattery,
                acPowered = acPowered,
                isCharging = isCharging,
                rssi = rssi,
                statusFlags = protocol?.statusFlags(statusWord)?.map { it.toDTO() } ?: emptyList(),
                openAlarms = openAlarms,
                openEvents = openEvents,
                configMismatchCount = configMismatchCount,
                decodeFailureCount = decodeFailureCount,
                configVersionOnWire = (configVersion and 0xFF).toInt(),
                lastReportedConfigVersion = lastReportedConfigVersion,
                cmdSeq = cmdSeq,
                appliedCmdSeq = appliedCmdSeq,
        )
