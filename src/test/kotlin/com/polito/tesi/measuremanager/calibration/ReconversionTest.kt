package com.polito.tesi.measuremanager.calibration

import com.polito.tesi.measuremanager.entities.ActiveCalibration
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Metric
import com.polito.tesi.measuremanager.entities.MetricSample
import com.polito.tesi.measuremanager.entities.ReconversionMode
import com.polito.tesi.measuremanager.entities.ReconversionState
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.repositories.ActiveCalibrationRepository
import com.polito.tesi.measuremanager.repositories.MetricSampleRepository
import com.polito.tesi.measuremanager.repositories.ReconversionRunRepository
import com.polito.tesi.measuremanager.template.SensorConversion
import com.polito.tesi.measuremanager.template.TemplateService
import com.polito.tesi.measuremanager.template.UncertaintyEvaluator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * La riconversione (PIANO_METROLOGIA 5.1): un certificato con as-found fuori tolleranza
 * corregge il periodo precedente senza toccare `value`, e il periodo fra as-found e attivazione
 * si riconverte con la funzione nuova.
 */
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReconversionService::class, ActiveCalibrationService::class, SensorConversion::class, UncertaintyEvaluator::class)
class ReconversionTest {

    @MockBean lateinit var templates: TemplateService

    @Autowired lateinit var service: ReconversionService
    @Autowired lateinit var activations: ActiveCalibrationService
    @Autowired lateinit var calibrations: ActiveCalibrationRepository
    @Autowired lateinit var samples: MetricSampleRepository
    @Autowired lateinit var runs: ReconversionRunRepository
    @Autowired lateinit var em: EntityManager

    companion object {
        private val postgres: EmbeddedPostgres =
                EmbeddedPostgres.builder()
                        .apply {
                            if (System.getProperty("java.io.tmpdir").contains(' ')) {
                                setOverrideWorkingDirectory(File("C:/Users/Public/embedded-pg").apply { mkdirs() })
                            }
                        }
                        .start()

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.getJdbcUrl("postgres", "postgres") }
            registry.add("spring.datasource.username") { "postgres" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }

        @JvmStatic
        @AfterAll
        fun stop() = postgres.close()

        const val EUID = 0x1A2B3C4DL
        val t0: OffsetDateTime = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        fun linear(offset: Double) =
                mapOf("type" to "linear_lsb", "linear_lsb" to "c[0] + c[1] * x", "input" to listOf("x"),
                        "c" to listOf(mapOf("id" to 0, "value" to offset), mapOf("id" to 1, "value" to 0.01)))
    }

    private fun sensor(): Sensor {
        val mu = MeasurementUnit().apply { extendedId = EUID }
        em.persist(mu)
        val sensor = Sensor(modelName = "TemperatureSensorNTC", measurementUnit = mu, sensorIndex = 0, configurationMeasure = "mean-variance")
        em.persist(sensor)
        return sensor
    }

    private fun sample(sensor: Sensor, at: OffsetDateTime, value: Double, calibration: ActiveCalibration?) =
            samples.save(
                    MetricSample(
                            sensor = sensor,
                            timestamp = at,
                            metric = Metric.MEAN,
                            value = value,
                            rawValue = 2000.0,
                            converted = true,
                            calibration = calibration,
                            uStd = 0.1,
                            uComponents = mapOf(UncertaintyEvaluator.CALIBRATION to 0.099, UncertaintyEvaluator.RESOLUTION to 0.003),
                            windowEnd = at,
                    )
            )

    private fun event(validFrom: OffsetDateTime, offset: Double, certificate: Long, asFoundAt: OffsetDateTime?, driftFrom: OffsetDateTime?) =
            CalibrationActivatedEvent(
                    muExtendedId = EUID,
                    sensorIndex = 0,
                    certificateId = certificate,
                    certificateNumber = "LAB-2026-%04d".format(certificate),
                    validFrom = validFrom,
                    function = linear(offset),
                    covariance = listOf(listOf(0.0025, 0.0), listOf(0.0, 1e-10)),
                    asFoundAt = asFoundAt,
                    driftFrom = driftFrom,
            )

    private fun runAll() {
        while (service.step(batch = 2)) em.flush()
        em.flush()
        em.clear()
    }

    @Test
    fun `peso e correzione della deriva`() {
        assertEquals(0.25, ReconversionService.weight(t0.plusDays(25), t0, t0.plusDays(100)), 1e-12)
        assertEquals(1.0, ReconversionService.weight(t0.plusDays(120), t0, t0.plusDays(100)), "oltre l'as-found vale la funzione nuova")
        val (value, u) = ReconversionService.drift(20.0, 20.5, 0.5, 0.1)
        assertEquals(20.25, value, 1e-12)
        assertEquals(sqrt(0.01 + (0.25 / sqrt(3.0)) * (0.25 / sqrt(3.0))), u!!, 1e-12)
    }

    @Test
    fun `un as-found fuori tolleranza corregge il periodo precedente senza toccare value`() {
        val sensor = sensor()
        // La taratura precedente: in vigore dal giorno 0, funzione 0,01·x.
        activations.apply(null, event(t0, 0.0, 1, asFoundAt = null, driftFrom = null))
        val previous = calibrations.findByMuExtendedIdAndSensorIndexOrderByValidFromDesc(EUID, 0).single()
        val days = listOf(25L, 50L, 75L)
        val stored = days.map { sample(sensor, t0.plusDays(it), 20.0, previous) }
        // Fra as-found (giorno 100) e attivazione (giorno 103) i campioni usano ancora la vecchia.
        val inGap = sample(sensor, t0.plusDays(101), 20.0, previous)
        em.flush()

        // La taratura nuova: as-found al giorno 100 con 0,5 °C di scostamento, fuori tolleranza.
        activations.apply(null, event(t0.plusDays(103), 0.5, 2, asFoundAt = t0.plusDays(100), driftFrom = t0))
        activations.apply(null, event(t0.plusDays(103), 0.5, 2, asFoundAt = t0.plusDays(100), driftFrom = t0))
        em.flush()
        assertEquals(setOf(ReconversionMode.GAP, ReconversionMode.DRIFT), runs.findAll().map { it.mode }.toSet())
        assertEquals(2, runs.count(), "un'attivazione riletta non riprogramma niente")

        runAll()
        assertEquals(setOf(ReconversionState.DONE), runs.findAll().map { it.state }.toSet())

        for ((i, s) in stored.withIndex()) {
            val row = samples.findById(s.id).orElseThrow()
            val w = days[i] / 100.0
            assertEquals(20.0, row.value, "value non si aggiorna mai")
            assertEquals(20.0 + w * 0.5, row.correctedValue!!, 1e-9)
            assertEquals(sqrt(0.01 + (w * 0.5 / sqrt(3.0)) * (w * 0.5 / sqrt(3.0))), row.correctedU!!, 1e-9)
            assertNotNull(row.correctedRunId)
        }

        val gap = samples.findById(inGap.id).orElseThrow()
        assertEquals(20.0, gap.value)
        assertEquals(20.5, gap.correctedValue!!, 1e-9, "nel gap vale gia' la funzione nuova")
        // Taratura nuova √(gᵀΣg) con g = [1, 2000], risoluzione invariata (stessa pendenza).
        assertEquals(sqrt(0.0025 + 4e6 * 1e-10 + 0.003 * 0.003), gap.correctedU!!, 1e-9)
    }

    @Test
    fun `senza as-found nell'evento non si riconverte niente`() {
        sensor()
        activations.apply(null, event(t0, 0.0, 1, asFoundAt = null, driftFrom = null))
        em.flush()
        assertEquals(0, runs.count())
        assertNull(runs.findFirstByStateInOrderByIdAsc(listOf(ReconversionState.PENDING)))
    }
}
