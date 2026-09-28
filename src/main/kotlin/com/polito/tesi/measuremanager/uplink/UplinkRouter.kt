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
 * Le porte del formato precedente restano accese: si spengono quando ogni CU in campo
 * dichiara la ProtoVer 1.2 nel poll, non un minuto prima.
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
                FPort.POLL, FPort.CU_STATUS_LEGACY -> poll(uplink, bytes)
                FPort.TOPOLOGY, FPort.TOPOLOGY_LEGACY -> topology(uplink, bytes)
                FPort.MU_STATUS -> muStatus(uplink, bytes)
                FPort.REPORT -> report(uplink, bytes)
                FPort.REPORT_LEGACY -> legacyReport(uplink, bytes)
                FPort.ALARMS -> alarmService.onAlarms(uplink)
                FPort.EVENTS -> alarmService.onEvents(uplink)
                // Un byte che serve solo ad aprire una finestra di ricezione: non c'e' niente
                // da leggere, e riceverlo e' gia' l'informazione.
                FPort.DUMMY -> log.debug("DevEUI={}: dummy", uplink.devEui)
                FPort.REPORT_LEGACY_EXTRA ->
                        cus.recordUndecodableFrame(
                                devEui = uplink.devEui,
                                fport = uplink.fport,
                                rawPayload = uplink.rawPayload,
                                reason =
                                        "Porta 0x31 dismessa: le metriche extra viaggiano nel CONTENT della 0x30",
                                timestamp = uplink.timestamp,
                        )
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

    /** Report nel formato precedente: un byte di CFG_VER e via, senza CONTENT ne' FRAG. */
    private fun legacyReport(uplink: LoraUplink, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            log.warn("DevEUI={}: report 0x21 vuoto", uplink.devEui)
            return
        }
        cus.onMeasuresUpdate(
                CuMeasuresUpdate(
                        devEui = uplink.devEui,
                        deviceId = uplink.deviceId,
                        configVersion = bytes[0].toInt() and 0xFF,
                        rawPayload =
                                Base64.getEncoder().encodeToString(bytes.copyOfRange(1, bytes.size)),
                        timestamp = uplink.timestamp,
                        content = null,
                        fragments = 1,
                        fport = FPort.REPORT_LEGACY,
                )
        )
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
