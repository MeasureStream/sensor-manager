package com.polito.tesi.measuremanager.uplink

import com.polito.tesi.measuremanager.dtos.CuJoinNotification
import com.polito.tesi.measuremanager.dtos.CuMeasuresUpdate
import com.polito.tesi.measuremanager.dtos.CuStatusUpdate
import com.polito.tesi.measuremanager.dtos.LoraUplink
import com.polito.tesi.measuremanager.dtos.MuDescriptor
import com.polito.tesi.measuremanager.dtos.MuStatusReport
import com.polito.tesi.measuremanager.dtos.MuStatusUpdate
import com.polito.tesi.measuremanager.services.AlarmService
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import com.polito.tesi.measuremanager.services.MuStatusService
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Dove un uplink diventa un fatto: dai byte grezzi al servizio che sa cosa farne.
 *
 * Nel protocollo v1.2 la FPort **e'** il codice operativo, quindi tutto l'instradamento e' un
 * solo `when`. Sta in sensor-manager e non nel trasporto perche' leggere un payload richiede
 * i template, la topologia della CU e l'istantanea della configurazione: tre cose che vivono
 * qui e che qui si aggiornano nella stessa transazione. Prima erano divise fra due servizi, e
 * un formato nuovo obbligava a rilasciarli entrambi.
 *
 * Le porte del formato precedente sono state dismesse il 26 ottobre 2026, quando ogni CU in
 * campo ha dichiarato la ProtoVer 1.2 nel poll. Non spariscono in silenzio: arrivarci sopra
 * lascia un frame registrato con il motivo, perche' un dispositivo rimasto indietro va visto,
 * non ignorato.
 */
@Service
class UplinkRouter(
        private val cus: ControlUnitServiceImpl,
        private val alarmService: AlarmService,
        private val muStatusService: MuStatusService,
        private val pollDecoder: PollDecoder,
        private val topologyDecoder: TopologyDecoder,
        private val muStatusDecoder: MuStatusDecoder,
        private val assembler: ReportAssembler,
) {
    private val log = LoggerFactory.getLogger(UplinkRouter::class.java)

    private companion object {
        /** Le porte che il server non legge piu'. Restano nominate per poterle riconoscere. */
        val DISMESSE =
                setOf(
                        FPort.CU_STATUS_LEGACY,
                        FPort.TOPOLOGY_LEGACY,
                        FPort.REPORT_LEGACY,
                        FPort.REPORT_LEGACY_EXTRA,
                )
    }

    fun accept(uplink: LoraUplink) {
        val bytes =
                try {
                    Base64.getDecoder().decode(uplink.rawPayload)
                } catch (e: Exception) {
                    log.warn(
                            "DevEUI={} porta {}: payload non in base64",
                            uplink.devEui,
                            FPort.label(uplink.fport),
                    )
                    return
                }

        try {
            when (uplink.fport) {
                FPort.POLL -> poll(uplink, bytes)
                FPort.TOPOLOGY -> topology(uplink, bytes)
                FPort.MU_STATUS -> muStatus(uplink, bytes)
                FPort.REPORT -> report(uplink, bytes)
                FPort.ALARMS -> alarmService.onAlarms(uplink)
                FPort.EVENTS -> alarmService.onEvents(uplink)
                // Un byte che serve solo ad aprire una finestra di ricezione: non c'e' niente
                // da leggere, e riceverlo e' gia' l'informazione.
                FPort.DUMMY -> log.debug("DevEUI={}: dummy", uplink.devEui)
                in DISMESSE -> dismissed(uplink)
                else ->
                        log.warn(
                                "DevEUI={}: nessun decoder per la porta {}",
                                uplink.devEui,
                                FPort.label(uplink.fport),
                        )
            }
        } catch (e: Exception) {
            // Un messaggio illeggibile non deve fermare quelli che seguono: si registra e si
            // va avanti, perche' la coda e' condivisa da tutte le CU.
            log.error(
                    "DevEUI={} porta {}: {}",
                    uplink.devEui,
                    FPort.label(uplink.fport),
                    e.message,
                    e,
            )
        }

        // L'ultimo contatto si segna qui, su qualunque porta e anche se la lettura e'
        // fallita: e' arrivato qualcosa da quella CU, ed e' tutto cio' che significa. Dopo
        // l'instradamento, cosi' una CU appena censita dal poll esiste gia'.
        cus.touch(uplink.devEui, uplink.timestamp)
    }

    /* -------------------------------------------------- porte dismesse */

    /**
     * Una porta del formato precedente.
     *
     * Si registra come frame illeggibile invece di finire in un log: se un dispositivo e'
     * rimasto indietro lo si deve poter vedere dalla scheda della CU, con il grezzo accanto,
     * e non scoprirlo perche' mancano le misure.
     */
    private fun dismissed(uplink: LoraUplink) {
        cus.recordUndecodableFrame(
                devEui = uplink.devEui,
                fport = uplink.fport,
                rawPayload = uplink.rawPayload,
                reason =
                        "Porta ${FPort.label(uplink.fport)} dismessa con la v1.2: " +
                                "il dispositivo parla ancora il formato precedente",
                timestamp = uplink.timestamp,
        )
    }

    /* ------------------------------------------------------------- poll */

    private fun poll(uplink: LoraUplink, bytes: ByteArray) {
        val poll = pollDecoder.decode(bytes, uplink.fport)
        cus.onStatusUpdate(
                CuStatusUpdate(
                        devEui = uplink.devEui,
                        deviceId = uplink.deviceId,
                        model = poll.model,
                        batteryLevel = poll.batteryLevel,
                        ptx = poll.transmissionPower,
                        acPowered = poll.acPowered,
                        isCharging = poll.charging,
                        statusRaw = poll.statusWord.toInt(),
                        configVersion = poll.cfgVersion,
                        appliedCmdSeq = poll.appliedCmdSeq,
                        protocolVer = poll.protocolVer,
                        alarmSeq = poll.alarmSeq,
                )
        )
    }

    /* -------------------------------------------------------- topologia */

    private fun topology(uplink: LoraUplink, bytes: ByteArray) {
        val entries = topologyDecoder.decode(bytes, uplink.fport)
        cus.onJoinNotification(
                CuJoinNotification(
                        devEui = uplink.devEui,
                        deviceId = uplink.deviceId,
                        muList =
                                entries.map {
                                    MuDescriptor(it.extendedId, it.localId, it.model, it.modelMajor)
                                },
                )
        )
        log.info(
                "DevEUI={}: topologia con {} MU dalla porta {}",
                uplink.devEui,
                entries.size,
                FPort.label(uplink.fport),
        )
    }

    private fun muStatus(uplink: LoraUplink, bytes: ByteArray) {
        val message = muStatusDecoder.decode(bytes)
        muStatusService.onMuStatus(
                MuStatusUpdate(
                        devEui = uplink.devEui,
                        deviceId = uplink.deviceId,
                        statusSeq = message.statusSeq,
                        entries =
                                message.entries.map {
                                    MuStatusReport(it.localId, it.statusWord.toInt())
                                },
                )
        )
    }

    /* ----------------------------------------------------------- report */

    private fun report(uplink: LoraUplink, bytes: ByteArray) {
        val (header, data) = assembler.parseHeader(bytes)
        val result = assembler.accept(uplink.devEui, header, data)

        result.abandoned?.let { record(uplink, it) }

        result.complete?.let { assembled ->
            cus.onMeasuresUpdate(
                    CuMeasuresUpdate(
                            devEui = uplink.devEui,
                            deviceId = uplink.deviceId,
                            configVersion = assembled.cfgVersion,
                            rawPayload = Base64.getEncoder().encodeToString(assembled.data),
                            timestamp = uplink.timestamp,
                            content = assembled.content,
                            fragments = assembled.fragments,
                            fport = FPort.REPORT,
                    )
            )
        }
    }

    /**
     * I report rimasti a meta' non si scoprono da soli: se l'ultimo frammento non arriva mai,
     * nessun messaggio successivo va a guardare quell'accumulo. Questa passata lo fa.
     */
    @Scheduled(fixedDelay = 300_000L)
    fun sweepIncompleteReports() {
        assembler.expire().forEach { abandoned ->
            cus.recordUndecodableFrame(
                    devEui = abandoned.devEui,
                    fport = FPort.REPORT,
                    rawPayload = Base64.getEncoder().encodeToString(abandoned.data),
                    reason = "Report incompleto: ${abandoned.reason}",
                    cfgVersion = abandoned.cfgVersion,
            )
        }
    }

    private fun record(uplink: LoraUplink, abandoned: AbandonedReport) {
        cus.recordUndecodableFrame(
                devEui = abandoned.devEui,
                fport = FPort.REPORT,
                rawPayload = Base64.getEncoder().encodeToString(abandoned.data),
                reason = "Report incompleto: ${abandoned.reason}",
                cfgVersion = abandoned.cfgVersion,
                timestamp = uplink.timestamp,
        )
    }
}
