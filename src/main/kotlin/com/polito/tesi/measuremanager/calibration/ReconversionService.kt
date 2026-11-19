package com.polito.tesi.measuremanager.calibration

import com.polito.tesi.measuremanager.entities.ActiveCalibration
import com.polito.tesi.measuremanager.entities.Metric
import com.polito.tesi.measuremanager.entities.MetricSample
import com.polito.tesi.measuremanager.entities.ReconversionMode
import com.polito.tesi.measuremanager.entities.ReconversionRun
import com.polito.tesi.measuremanager.entities.ReconversionState
import com.polito.tesi.measuremanager.repositories.ActiveCalibrationRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.ReconversionRunRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.template.ConversionFunction
import com.polito.tesi.measuremanager.template.MetrologySpec
import com.polito.tesi.measuremanager.template.SensorConversion
import com.polito.tesi.measuremanager.template.SensorTemplate
import com.polito.tesi.measuremanager.template.TemplateService
import com.polito.tesi.measuremanager.template.UncertaintyEvaluator
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * La riconversione dei campioni gia' registrati (PIANO_METROLOGIA 5.1, specifica § 4.4).
 *
 * `value` non si aggiorna mai: il valore rivalutato va in `corrected_value`, con l'incertezza,
 * la taratura e la riconversione che l'hanno prodotto. Due modi:
 *
 * - **GAP** — i campioni fra l'as-found della taratura nuova e la sua attivazione sono stati
 *   convertiti con la funzione vecchia, ma lo strumento era gia' quello descritto dalla nuova:
 *   si riconvertono con la nuova. L'incertezza si ricompone dai contributi salvati con il
 *   campione: la taratura diventa `√(gᵀΣg)` della funzione nuova, risoluzione e tipo A si
 *   riscalano con la sensibilita' nuova, la deriva riparte dall'as-found.
 * - **DRIFT** — un as-found fuori tolleranza dice che lo strumento e' derivato dall'as-left
 *   precedente: nel periodo fra i due la funzione al tempo t e' la media pesata
 *   `f_t = (1 − w)·f_vecchia + w·f_nuova`, con w proporzionale al tempo trascorso. Il valore
 *   corretto e' quindi `value + w·(f_nuova(x) − value)`, e all'incertezza si aggiunge la
 *   correzione divisa per √3, perche' non si sa se la deriva fosse davvero lineare.
 *
 * Solo le metriche che passano per la funzione di taratura (media, massimo, minimo, mediana,
 * percentili, puntuale) si correggono; la varianza solo nel modo GAP, dove la funzione e' una.
 * Si lavora a lotti in background ([ReconversionWorker]): un anno di dati sono centinaia di
 * migliaia di righe, che non stanno in una transazione del consumer Kafka.
 */
@Service
class ReconversionService(
        private val runs: ReconversionRunRepository,
        private val calibrations: ActiveCalibrationRepository,
        private val templates: TemplateService,
        private val conversion: SensorConversion,
        private val uncertainty: UncertaintyEvaluator,
        private val em: EntityManager,
) {
    private val log = LoggerFactory.getLogger(ReconversionService::class.java)

    companion object {
        const val AUTOMATIC = "calibration-activated"
        const val BATCH = 2000

        /** Le metriche che passano per la funzione di taratura. */
        val CALIBRATED = setOf(Metric.MEAN, Metric.MAX, Metric.MIN, Metric.MEDIAN, Metric.P_HIGH, Metric.P_LOW, Metric.PUNCTUAL)

        /** Il peso della funzione nuova all'istante [t], fra [from] (as-left) e [to] (as-found). */
        fun weight(t: OffsetDateTime, from: OffsetDateTime, to: OffsetDateTime): Double {
            val span = Duration.between(from, to).toMillis().toDouble()
            if (span <= 0) return 1.0
            return (Duration.between(from, t).toMillis() / span).coerceIn(0.0, 1.0)
        }

        /** Il valore corretto per deriva e la sua incertezza tipo. */
        fun drift(value: Double, newValue: Double, w: Double, uStd: Double?): Pair<Double, Double?> {
            val correction = w * (newValue - value)
            val u = uStd?.let { sqrt(it * it + (correction / sqrt(3.0)).let { c -> c * c }) }
            return value + correction to u
        }
    }

    /**
     * Le riconversioni che un'attivazione rende necessarie. Idempotente: il topic compattato si
     * rilegge, e la stessa attivazione non riprogramma niente.
     */
    @Transactional
    fun planAfterActivation(event: CalibrationActivatedEvent, record: ActiveCalibration) {
        val asFound = event.asFoundAt ?: return
        val previous =
                calibrations.findByMuExtendedIdAndSensorIndexOrderByValidFromDesc(record.muExtendedId, record.sensorIndex)
                        .firstOrNull { it.id != record.id && it.validFrom.isBefore(record.validFrom) && it.revokedAt == null }
        if (asFound.isBefore(event.validFrom) && previous?.function != record.function) {
            plan(record, ReconversionMode.GAP, asFound, event.validFrom, "attivazione del certificato ${record.certificateNumber ?: record.certificateId}")
        }
        val driftFrom = event.driftFrom
        if (driftFrom != null && driftFrom.isBefore(asFound)) {
            plan(record, ReconversionMode.DRIFT, driftFrom, asFound, "as-found fuori tolleranza nel certificato ${record.certificateNumber ?: record.certificateId}")
        }
    }

    private fun plan(record: ActiveCalibration, mode: ReconversionMode, from: OffsetDateTime, to: OffsetDateTime, reason: String) {
        if (runs.findByCalibrationIdAndModeAndRequestedBy(record.id, mode, AUTOMATIC) != null) return
        val run =
                runs.save(
                        ReconversionRun(
                                muExtendedId = record.muExtendedId,
                                sensorIndex = record.sensorIndex,
                                calibrationId = record.id,
                                certificateId = record.certificateId,
                                mode = mode,
                                fromTs = from,
                                toTs = to,
                                requestedBy = AUTOMATIC,
                                reason = reason,
                        )
                )
        log.info("Riconversione {} programmata per {}:{} dal {} al {}", mode, record.muExtendedId, record.sensorIndex, from, to)
        run.id
    }

    /** Una riconversione chiesta da una persona: per esempio dopo una non conformita' di strumento. */
    @Transactional
    fun request(muExtendedId: Long, sensorIndex: Int, request: ReconversionRequest, by: String): ReconversionRun {
        val record =
                calibrations.findByIdOrNull(request.calibrationId)?.takeIf { it.muExtendedId == muExtendedId && it.sensorIndex == sensorIndex }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La taratura ${request.calibrationId} non e' di questo sensore")
        if (!request.to.isAfter(request.from)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "L'intervallo e' vuoto")
        if (request.reason.isNullOrBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Una riconversione si chiede con il motivo")
        return runs.save(
                ReconversionRun(
                        muExtendedId = muExtendedId,
                        sensorIndex = sensorIndex,
                        calibrationId = record.id,
                        certificateId = record.certificateId,
                        mode = request.mode,
                        fromTs = request.from,
                        toTs = request.to,
                        requestedBy = by,
                        reason = request.reason,
                )
        )
    }

    /**
     * Un lotto della riconversione piu' vecchia ancora aperta. Vero se resta lavoro: il worker
     * richiama finche' non finisce.
     */
    @Transactional
    fun step(batch: Int = BATCH): Boolean {
        val run = runs.findFirstByStateInOrderByIdAsc(listOf(ReconversionState.PENDING, ReconversionState.RUNNING)) ?: return false
        if (run.state == ReconversionState.PENDING) {
            run.state = ReconversionState.RUNNING
            run.startedAt = OffsetDateTime.now()
        }
        try {
            val processed = process(run, batch)
            if (processed < batch) {
                run.state = ReconversionState.DONE
                run.finishedAt = OffsetDateTime.now()
                log.info("Riconversione {} conclusa: {} campioni corretti, {} saltati", run.id, run.samples, run.skipped)
            }
        } catch (e: Exception) {
            log.warn("Riconversione {} fallita: {}", run.id, e.message)
            run.state = ReconversionState.FAILED
            run.error = e.message?.take(2000)
            run.finishedAt = OffsetDateTime.now()
        }
        runs.save(run)
        return true
    }

    private fun process(run: ReconversionRun, batch: Int): Int {
        val record = calibrations.findByIdOrNull(run.calibrationId) ?: throw IllegalStateException("taratura ${run.calibrationId} sparita")
        val newFunction = ConversionFunction.of(record.function) ?: throw IllegalStateException("la taratura ${record.id} non ha una formula leggibile")
        val sensors = sensorIds(run.muExtendedId, run.sensorIndex)
        if (sensors.isEmpty()) return 0

        val rows =
                em.createQuery(
                                "select m from MetricSample m join fetch m.sensor s left join fetch m.calibration " +
                                        "where s.id in :sensors and m.id > :cursor " +
                                        "and coalesce(m.windowEnd, m.timestamp) >= :from and coalesce(m.windowEnd, m.timestamp) < :to " +
                                        "order by m.id",
                                MetricSample::class.java,
                        )
                        .setParameter("sensors", sensors)
                        .setParameter("cursor", run.cursorId)
                        .setParameter("from", run.fromTs)
                        .setParameter("to", run.toTs)
                        .setMaxResults(batch)
                        .resultList
        if (rows.isEmpty()) return 0

        val templateCache = mutableMapOf<String, SensorTemplate?>()
        // La varianza si converte con la media della stessa finestra: le si cerca nel lotto.
        val means = rows.filter { it.metric == Metric.MEAN }.associateBy { it.sensor.id to it.timestamp }
        val now = OffsetDateTime.now()
        for (sample in rows) {
            val template = templateCache.getOrPut(sample.sensor.modelName ?: "") { sample.sensor.modelName?.let { templates.getTemplate(it) } }
            val corrected =
                    when (run.mode) {
                        ReconversionMode.GAP -> gap(sample, template, newFunction, record, run.fromTs, means[sample.sensor.id to sample.timestamp])
                        ReconversionMode.DRIFT -> drift(sample, template, newFunction, run)
                    }
            if (corrected == null) {
                run.skipped += 1
            } else {
                sample.correctedValue = corrected.first
                sample.correctedU = corrected.second
                sample.correctedCalibrationId = record.id
                sample.correctedAt = now
                sample.correctedRunId = run.id
                run.samples += 1
            }
            run.cursorId = sample.id
        }
        return rows.size
    }

    private fun gap(
            sample: MetricSample,
            template: SensorTemplate?,
            function: ConversionFunction,
            record: ActiveCalibration,
            asFound: OffsetDateTime,
            mean: MetricSample?,
    ): Pair<Double, Double?>? {
        val raw = sample.rawValue ?: return null
        if (sample.metric == Metric.VARIANCE) {
            val rawMean = mean?.rawValue ?: return null
            val converted = conversion.varianceToPhysical(template, rawMean, raw, function)
            return if (converted.converted && converted.value != null) converted.value to null else null
        }
        if (sample.metric !in CALIBRATED) return null
        val converted = conversion.toPhysical(template, raw, function)
        val value = converted.value ?: return null
        if (!converted.converted) return null
        return value to recomposedU(sample, template, function, record, raw, value, asFound)
    }

    /**
     * L'incertezza ricomposta dai contributi salvati: taratura nuova, risoluzione e tipo A
     * riscalati con la sensibilita' nuova, deriva dall'as-found. Senza contributi salvati (righe
     * storiche) non si inventa un numero: null.
     */
    private fun recomposedU(
            sample: MetricSample,
            template: SensorTemplate?,
            function: ConversionFunction,
            record: ActiveCalibration,
            raw: Double,
            value: Double,
            asFound: OffsetDateTime,
    ): Double? {
        val old = sample.uComponents ?: return null
        val spec = MetrologySpec.of(template?.metrology)
        val oldFunction = sample.calibration?.function?.let { ConversionFunction.of(it) } ?: conversion.functionOf(template)
        val oldSlope = oldFunction?.derivative(raw)
        val newSlope = function.derivative(raw)
        val scale = if (oldSlope != null && newSlope != null && oldSlope != 0.0) abs(newSlope / oldSlope) else 1.0
        val calibration =
                record.covariance?.let { uncertainty.instanceTerm(function, it, raw) }
                        ?: spec.mpeAt(value)?.let { it / sqrt(3.0) }
                        ?: return null
        val at = sample.windowEnd ?: sample.timestamp
        val drift =
                spec.drift?.let { d -> abs(d.ratePerSecond()) * Duration.between(asFound, at).seconds.coerceAtLeast(0) / sqrt(3.0) }
        val terms =
                listOfNotNull(
                        calibration,
                        old[UncertaintyEvaluator.RESOLUTION]?.times(scale),
                        old[UncertaintyEvaluator.TYPE_A]?.times(scale),
                        drift,
                        old[UncertaintyEvaluator.INFLUENCES],
                )
        return sqrt(terms.sumOf { it * it })
    }

    private fun drift(sample: MetricSample, template: SensorTemplate?, function: ConversionFunction, run: ReconversionRun): Pair<Double, Double?>? {
        if (sample.metric !in CALIBRATED) return null
        val raw = sample.rawValue ?: return null
        val value = sample.value ?: return null
        if (sample.converted != true) return null
        val converted = conversion.toPhysical(template, raw, function)
        val newValue = converted.value ?: return null
        if (!converted.converted) return null
        val w = weight(sample.windowEnd ?: sample.timestamp, run.fromTs, run.toTs)
        return drift(value, newValue, w, sample.uStd)
    }

    private fun sensorIds(muExtendedId: Long, sensorIndex: Int): List<Long> {
        @Suppress("UNCHECKED_CAST")
        val ids =
                em.createNativeQuery(
                                "SELECT s.id FROM public.sensor s JOIN public.measurement_unit mu ON mu.id = s.mu_id " +
                                        "WHERE mu.extended_id = :mu AND s.sensor_index = :idx"
                        )
                        .setParameter("mu", muExtendedId)
                        .setParameter("idx", sensorIndex)
                        .resultList as List<Number>
        return ids.map { it.toLong() }
    }

    @Transactional(readOnly = true)
    fun list(muExtendedId: Long, sensorIndex: Int): List<ReconversionRun> = runs.findAllByMuExtendedIdAndSensorIndexOrderByIdDesc(muExtendedId, sensorIndex)
}

/** Lavora le riconversioni aperte a lotti, fuori dalle transazioni dei consumer. */
@Component
class ReconversionWorker(private val service: ReconversionService) {
    @Scheduled(fixedDelayString = "\${reconversion.delay-ms:5000}", initialDelayString = "\${reconversion.initial-delay-ms:30000}")
    fun tick() {
        // Al massimo venti lotti per giro: la Pi resta libera per i report che arrivano.
        var rounds = 0
        while (rounds < 20 && service.step()) rounds++
    }
}

data class ReconversionRequest(
        val calibrationId: Long,
        val mode: ReconversionMode,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
        val reason: String? = null,
)

/**
 * Le riconversioni di un sensore. Stanno sotto `/API/measurementunits` come le altre letture
 * metrologiche: i permessi sono quelli della MU.
 */
@RestController
@RequestMapping("/API/measurementunits")
class ReconversionController(
        private val service: ReconversionService,
        private val units: MeasurementUnitRepository,
        private val security: SecurityService,
) {
    @GetMapping("/{id}/sensors/{sensorId}/reconversions")
    fun list(@PathVariable id: Long, @PathVariable sensorId: Long): List<ReconversionRun> {
        val (euid, index) = sensor(id, sensorId)
        return service.list(euid, index)
    }

    @PostMapping("/{id}/sensors/{sensorId}/reconversions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(@PathVariable id: Long, @PathVariable sensorId: Long, @RequestBody request: ReconversionRequest): ReconversionRun {
        val (euid, index) = sensor(id, sensorId)
        return service.request(euid, index, request, security.getCurrentUserId())
    }

    private fun sensor(id: Long, sensorId: Long): Pair<Long, Int> {
        val mu =
                (if (security.isAdmin()) units.findByIdOrNull(id) else units.findByIdAndUser_UserId(id, security.getCurrentUserId()))
                        ?: throw EntityNotFoundException("Measurement Unit $id non trovata")
        val sensor = mu.sensors.firstOrNull { it.id == sensorId } ?: throw EntityNotFoundException("Sensore $sensorId non trovato sulla MU $id")
        val euid = mu.extendedId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "La MU $id non ha ancora un EUID")
        return euid to sensor.sensorIndex
    }
}
