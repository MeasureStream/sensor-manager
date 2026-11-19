package com.polito.tesi.measuremanager.schema

import com.polito.tesi.measuremanager.entities.ActiveCalibration
import com.polito.tesi.measuremanager.entities.OutboxEvent
import com.polito.tesi.measuremanager.repositories.ActiveCalibrationRepository
import com.polito.tesi.measuremanager.repositories.OutboxEventRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.time.OffsetDateTime
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Le migrazioni Flyway su un PostgreSQL vero, e Hibernate che valida le entita' contro lo
 * schema che ne esce (`ddl-auto: validate`). Gira senza Docker: il database e' un processo
 * PostgreSQL incorporato, avviato una volta per la classe.
 */
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaMigrationTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var calibrations: ActiveCalibrationRepository
    @Autowired lateinit var outbox: OutboxEventRepository

    companion object {
        /** Su Windows la cartella temporanea dell'utente puo' avere spazi, che initdb non regge. */
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
    }

    @Test
    fun `le migrazioni arrivano alla V25 e lo schema valida le entita'`() {
        val version = jdbc.queryForObject("select max(version::int) from flyway_schema_history where success", Int::class.java)
        assertEquals(25, version)
    }

    @Test
    fun `active_calibration e metric_sample con le colonne nuove`() {
        val saved =
                calibrations.saveAndFlush(
                        ActiveCalibration(
                                muExtendedId = 439041101,
                                sensorIndex = 2,
                                certificateId = 128,
                                function = mapOf("type" to "linear", "linear" to "c[0] + c[1] * x", "c" to listOf(mapOf("id" to 0, "value" to 1.0))),
                                covariance = listOf(listOf(1e-4)),
                                validFrom = OffsetDateTime.parse("2026-12-03T10:42:00Z"),
                        )
                )
        assertNotNull(saved.id)
        val found = calibrations.findByMuExtendedIdAndSensorIndexOrderByValidFromDesc(439041101, 2).single()
        assertEquals(listOf(listOf(1e-4)), found.covariance)

        val columns =
                jdbc.queryForList(
                        "select column_name from information_schema.columns where table_name = 'metric_sample'",
                        String::class.java,
                )
        listOf("calibration_id", "u_std", "u_components", "quality_flags", "window_start", "window_end").forEach {
            assert(it in columns) { "manca metric_sample.$it" }
        }
    }

    @Test
    fun `outbox e viste di Grafana`() {
        outbox.saveAndFlush(OutboxEvent(topic = "sample-converted", eventKey = "1", payload = mapOf("a" to 1)))
        assertEquals(1, outbox.findTop200BySentAtIsNullOrderByIdAsc().size)
        jdbc.queryForList("select incertezza_estesa, banda_inf, banda_sup, flag_qualita from grafana.measure_avg_std limit 1")
        jdbc.queryForList("select * from grafana.sensor_calibration limit 1")
        jdbc.queryForList("select gap_seconds, expected_seconds, missing_reports, is_gap from grafana.report_gaps limit 1")
        jdbc.queryForList("select mu_extended_id, sensor_index, metric, \"time\", value, banda_inf, banda_sup, valore_corretto from grafana.sample_series limit 1")
        jdbc.queryForList("select media_corretta, incertezza_estesa_corretta from grafana.measure_avg_std limit 1")
        jdbc.queryForList("select mode, state, cursor_id, samples from public.reconversion_run limit 1")
    }
}
