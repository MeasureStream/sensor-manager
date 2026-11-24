package com.polito.tesi.measuremanager.quality

import com.polito.tesi.measuremanager.calibration.ActiveCalibrationService
import com.polito.tesi.measuremanager.calibration.CalibrationKafkaConfig
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.FrameStatus
import com.polito.tesi.measuremanager.entities.MetricSample
import com.polito.tesi.measuremanager.entities.OutboxEvent
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.entities.UplinkFrame
import com.polito.tesi.measuremanager.repositories.OutboxEventRepository
import com.polito.tesi.measuremanager.repositories.UplinkFrameRepository
import com.polito.tesi.measuremanager.template.ElapsedTime
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.SlotContext
import com.polito.tesi.measuremanager.uplink.FPort
import java.time.Duration
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Il quando e il quanto-fidarsi di un report: la sua finestra di tempo e i flag che valgono
 * per tutti i suoi campioni.
 */
data class ReportTiming(
        val receivedAt: OffsetDateTime,
        /** Inizio della finestra, se il periodo di trasmissione e' noto. */
        val windowStart: OffsetDateTime?,
        val windowEnd: OffsetDateTime,
        /** Eta' delle statistiche alla trasmissione, se il report la porta. */
        val ageSeconds: Long?,
        /** Il passo della fascia di Δt: e' l'incertezza sull'istante, in secondi. */
        val ageUncertaintySeconds: Int?,
        val transmissionSeconds: Double?,
        /** SNAPSHOT, TIME_UNSYNC, NO_AGE, GAP. */
        val flags: Int,
)

/**
 * Finestre di tempo, flag di qualita' e taratura per i campioni di un report (specifica
 * § 4.3, PIANO_METROLOGIA 2.4 e 2.5).
 *
 * **Il byte di eta'.** Il report v1.2 dice quando e' stato trasmesso, non quando le statistiche
 * sono state raccolte. Il dizionario 1.2.3 dichiara un bit di CONTENT che annuncia un byte di
 * eta' dopo FRAG; fino a che il firmware della CU non lo manda, la fine della finestra e'
 * l'istante di ricezione e il campione porta NO_AGE. E' un'approssimazione dichiarata, non un
 * errore: per un periodo di trasmissione di minuti sbaglia di meno di un periodo.
 */
@Service
class ReportQualityService(
        private val protocol: ProtocolService,
        private val frames: UplinkFrameRepository,
        private val calibrations: ActiveCalibrationService,
        private val outbox: OutboxEventRepository,
        private val activeCalibrations: com.polito.tesi.measuremanager.repositories.ActiveCalibrationRepository,
) {
    private val log = LoggerFactory.getLogger(ReportQualityService::class.java)

    /** Fra due report si tollera mezzo periodo di ritardo prima di parlare di un buco. */
    private val gapTolerance = 1.5

    fun timing(
            cu: ControlUnit,
            receivedAt: OffsetDateTime,
            ageRaw: Int?,
            readWithSnapshot: Boolean,
    ): ReportTiming {
        // 255 vuol dire «piu' vecchio del rappresentabile, o sconosciuto»: vale come assente.
        val age = ageRaw?.let { ElapsedTime.decode(it) }
        val windowEnd = age?.let { receivedAt.minusSeconds(it.seconds) } ?: receivedAt
        val transmission = protocol.transmissionPeriodSeconds(cu.transmissionInterval)
        val windowStart = transmission?.let { windowEnd.minusNanos((it * 1e9).toLong()) }

        var flags = 0
        if (age == null) flags = flags or QualityFlags.NO_AGE
        if (readWithSnapshot) flags = flags or QualityFlags.SNAPSHOT
        if (protocol.cuStatusHas(cu.statusWord, "time_sync_expired")) flags = flags or QualityFlags.TIME_UNSYNC
        if (gapBefore(cu, receivedAt, transmission)) flags = flags or QualityFlags.GAP

        if (ageRaw != null && age == null) {
            log.debug("CU devEUI={}: byte di eta' {} non rappresentabile, trattato come assente", cu.devEui, ageRaw)
        }

        return ReportTiming(
                receivedAt = receivedAt,
                windowStart = windowStart,
                windowEnd = windowEnd,
                ageSeconds = age?.seconds,
                ageUncertaintySeconds = age?.uncertaintySeconds,
                transmissionSeconds = transmission,
                flags = flags,
        )
    }

    /**
     * Manca almeno un report fra il precedente letto e questo? Si confronta l'intervallo con
     * il periodo di trasmissione della CU; senza periodo noto non si afferma niente. Vale anche
     * per i report scartati: nella serie dei dati sono un buco come quelli persi in radio.
     */
    private fun gapBefore(cu: ControlUnit, receivedAt: OffsetDateTime, transmission: Double?): Boolean {
        if (transmission == null || cu.id == 0L) return false
        val previous =
                frames.findTopByControlUnit_IdAndFportAndStatusInAndReceivedAtBeforeOrderByReceivedAtDesc(
                        cu.id,
                        FPort.REPORT,
                        listOf(FrameStatus.DECODED, FrameStatus.DECODED_WITH_SNAPSHOT),
                        receivedAt,
                ) ?: return false
        val elapsed = Duration.between(previous.receivedAt, receivedAt).seconds
        return elapsed > transmission * gapTolerance
    }

    /** Un riferimento alla riga della taratura, senza leggerla: basta per la chiave esterna. */
    fun calibrationRef(id: Long?) = id?.let { activeCalibrations.getReferenceById(it) }

    /**
     * Il motivo da aggiungere a uno scarto quando CONTENT alza bit che il dizionario non
     * conosce: e' la spiegazione piu' probabile di un report che non torna di un byte.
     */
    fun unknownContentHint(content: Int?): String {
        val unknown = protocol.unknownContentBits(content)
        if (unknown.isEmpty()) return ""
        return " — CONTENT alza ${if (unknown.size == 1) "il bit" else "i bit"} ${unknown.joinToString()} " +
                "che il dizionario pubblicato non descrive: probabilmente il firmware manda un campo " +
                "(come il byte di eta') che il dizionario non dichiara ancora"
    }

    /** La taratura in vigore e quanti campioni compongono le statistiche dello slot. */
    fun slotContext(sensor: Sensor, timing: ReportTiming): SlotContext {
        val mu = sensor.measurementUnit
        val calibration = mu?.let { calibrations.resolve(it.extendedId, sensor.sensorIndex, timing.windowEnd) }
        val sampling = protocol.samplingPeriodSeconds(sensor.samplingF.toInt())
        val samples =
                if (sampling != null && timing.transmissionSeconds != null) timing.transmissionSeconds / sampling
                else null
        return SlotContext(calibration = calibration, at = timing.windowEnd, samplesInWindow = samples)
    }

    /**
     * L'evento `sample-converted` del report, scritto nella stessa transazione dei campioni:
     * se la transazione fallisce sparisce con loro, se riesce il pubblicatore lo spedisce.
     */
    fun enqueueSampleConverted(
            cu: ControlUnit,
            frame: UplinkFrame?,
            timing: ReportTiming,
            samples: List<MetricSample>,
    ) {
        if (samples.isEmpty()) return
        val payload =
                mapOf(
                        "schemaVersion" to 1,
                        "devEui" to cu.devEui,
                        "controlUnitId" to cu.id,
                        "frameId" to frame?.id,
                        "receivedAt" to timing.receivedAt.toString(),
                        "windowStart" to timing.windowStart?.toString(),
                        "windowEnd" to timing.windowEnd.toString(),
                        "ageSeconds" to timing.ageSeconds,
                        "ageUncertaintySeconds" to timing.ageUncertaintySeconds,
                        "samples" to
                                samples.map { s ->
                                    mapOf(
                                            "muExtendedId" to s.sensor.measurementUnit?.extendedId,
                                            "sensorIndex" to s.sensor.sensorIndex,
                                            "sensorId" to s.sensor.id,
                                            "metric" to s.metric,
                                            "value" to s.value,
                                            "converted" to s.converted,
                                            "uStd" to s.uStd,
                                            "qualityFlags" to s.qualityFlags,
                                            "flagNames" to QualityFlags.names(s.qualityFlags),
                                            "certificateId" to s.calibration?.certificateId,
                                            "templateVersion" to s.templateVersion,
                                    )
                                },
                )
        outbox.save(
                OutboxEvent(
                        topic = CalibrationKafkaConfig.SAMPLE_CONVERTED_TOPIC,
                        eventKey = cu.devEui.toString(),
                        payload = payload,
                )
        )
    }
}
