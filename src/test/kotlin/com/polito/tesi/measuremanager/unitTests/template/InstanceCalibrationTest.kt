package com.polito.tesi.measuremanager.unitTests.template

import com.polito.tesi.measuremanager.calibration.ActiveCalibrationService
import com.polito.tesi.measuremanager.calibration.CalibratedRange
import com.polito.tesi.measuremanager.calibration.CalibrationActivatedEvent
import com.polito.tesi.measuremanager.entities.ActiveCalibration
import com.polito.tesi.measuremanager.entities.Metric
import com.polito.tesi.measuremanager.quality.QualityFlags
import com.polito.tesi.measuremanager.repositories.ActiveCalibrationRepository
import com.polito.tesi.measuremanager.template.MetricSpec
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.ReportDecoder
import com.polito.tesi.measuremanager.template.SensorConversion
import com.polito.tesi.measuremanager.template.SensorTemplate
import com.polito.tesi.measuremanager.template.SlotContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La taratura dell'esemplare cambia la conversione (prova di accettazione di I1): un
 * certificato attivato per un sensore vince sulla formula del modello, ogni campione dice
 * quale taratura l'ha prodotto, e il valore coincide con un calcolo a mano.
 */
class InstanceCalibrationTest {

    /** Il template di targa: y = -6 + 125·x/65536, l'umidita' dell'HTU21D. */
    private val template =
            SensorTemplate(
                    modelName = "HumiditySensorHTU21D",
                    type = "humidity",
                    unit = "\\percent",
                    supportedMetrics =
                            listOf(
                                    MetricSpec(id = 1, name = "mean", bytes = 2, transform = "calibration"),
                                    MetricSpec(id = 2, name = "variance", bytes = 2, transform = "variance"),
                            ),
                    calibration =
                            mapOf(
                                    "type" to "linear",
                                    "linear" to "c[0] + c[1] * x",
                                    "input" to listOf("x"),
                                    "c" to listOf(mapOf("id" to 0, "value" to -6.0), mapOf("id" to 1, "value" to 0.0019073486328125)),
                            ),
                    metrology = mapOf("mpe" to listOf(mapOf("value" to 2.0))),
            )

    /** Il certificato: y = -5 + 0.002·x, tarato fra 10 e 90 %RH. */
    private val event =
            CalibrationActivatedEvent(
                    muExtendedId = 439041101,
                    sensorIndex = 2,
                    calibratedItemId = 12,
                    certificateId = 128,
                    certificateNumber = "MS-2026-0012",
                    validFrom = OffsetDateTime.parse("2026-12-03T10:42:00Z"),
                    validUntil = OffsetDateTime.parse("2027-12-03T00:00:00Z"),
                    function =
                            mapOf(
                                    "type" to "linear",
                                    "linear" to "c[0] + c[1] * x",
                                    "input" to listOf("x"),
                                    "c" to listOf(mapOf("id" to 0, "value" to -5.0), mapOf("id" to 1, "value" to 0.002)),
                            ),
                    covariance = listOf(listOf(0.01, 0.0), listOf(0.0, 1e-12)),
                    calibratedRange = CalibratedRange(10.0, 90.0),
            )

    private val protocol = mockk<ProtocolService> { every { metrics() } returns emptyList() }
    private val decoder = ReportDecoder(SensorConversion(), protocol)

    private val stored = mutableListOf<ActiveCalibration>()
    private val repository =
            mockk<ActiveCalibrationRepository>(relaxed = true) {
                every { findByMuExtendedIdAndSensorIndexOrderByValidFromDesc(any(), any()) } answers
                        {
                            stored.filter { it.muExtendedId == firstArg<Long>() && it.sensorIndex == secondArg<Int>() }
                                    .sortedByDescending { it.validFrom }
                        }
                every { findByMuExtendedIdAndSensorIndexAndValidFrom(any(), any(), any()) } returns null
                val saved = slot<ActiveCalibration>()
                every { save(capture(saved)) } answers
                        {
                            saved.captured.also {
                                if (it.id == 0L) it.id = (stored.size + 1).toLong()
                                stored.removeIf { s -> s.id == it.id }
                                stored.add(it)
                            }
                        }
                every { findByMuExtendedIdAndSensorIndexAndRevokedAtIsNull(any(), any()) } answers
                        { stored.filter { it.revokedAt == null } }
            }
    private val service = ActiveCalibrationService(repository)

    private fun report(mean: Int, variance: Int): ByteBuffer =
            ByteBuffer.allocate(4).putShort(mean.toShort()).putShort(variance.toShort()).flip() as ByteBuffer

    @Test
    fun `senza taratura dell'esemplare vale la formula del modello`() {
        val decoded = decoder.decodeSlot(report(30000, 16), template, listOf("mean", "variance"), "slot 2")
        val mean = decoded.first { it.metric == Metric.MEAN }

        assertEquals(-6.0 + 30000 * 0.0019073486328125, mean.value!!, 1e-9)
        assertEquals(QualityFlags.CONVERTED or QualityFlags.CAL_NOMINAL, mean.flags, "convertito, con la formula di targa")
        assertNull(mean.calibrationId)
        assertEquals(2.0 / kotlin.math.sqrt(3.0), mean.uncertainty?.components?.get("calibration")!!, 1e-12)
    }

    @Test
    fun `il certificato attivato cambia la conversione e lo dichiara`() {
        service.apply(event.key(), event)
        val at = OffsetDateTime.parse("2027-01-10T08:00:00Z")
        val calibration = assertNotNull(service.resolve(439041101, 2, at))

        val decoded =
                decoder.decodeSlot(report(30000, 16), template, listOf("mean", "variance"), "slot 2", SlotContext(calibration, at))
        val mean = decoded.first { it.metric == Metric.MEAN }
        val variance = decoded.first { it.metric == Metric.VARIANCE }

        // A mano: -5 + 0.002 · 30000 = 55, contro i 51.22 della formula di targa.
        assertEquals(55.0, mean.value!!, 1e-9)
        assertEquals(QualityFlags.CONVERTED or QualityFlags.CAL_INSTANCE, mean.flags)
        assertEquals(calibration.record.id, mean.calibrationId)
        // La varianza si propaga con la derivata della funzione dell'esemplare: 16 · 0.002².
        assertEquals(16 * 0.002 * 0.002, variance.value!!, 1e-15)
        // Taratura: √(0.01 + 30000²·1e-12) = √0.0109.
        assertEquals(kotlin.math.sqrt(0.0109), mean.uncertainty?.components?.get("calibration")!!, 1e-6)
    }

    @Test
    fun `fuori dal campo tarato e dopo la scadenza i flag lo dicono`() {
        service.apply(event.key(), event)
        val late = OffsetDateTime.parse("2028-01-10T08:00:00Z")
        val calibration = assertNotNull(service.resolve(439041101, 2, late), "scaduta ma ancora in uso")
        assertTrue(calibration.expired)

        // -5 + 0.002 · 50000 = 95 %RH: oltre i 90 del campo tarato.
        val mean =
                decoder.decodeSlot(report(50000, 16), template, listOf("mean", "variance"), "slot 2", SlotContext(calibration, late))
                        .first { it.metric == Metric.MEAN }
        assertTrue(mean.flags and QualityFlags.CAL_EXPIRED != 0, "scaduta")
        assertTrue(mean.flags and QualityFlags.OUT_OF_CAL_RANGE != 0, "estrapolata")
    }

    @Test
    fun `prima dell'inizio validita' e dopo la revoca non vale`() {
        service.apply(event.key(), event)
        assertNull(service.resolve(439041101, 2, OffsetDateTime.parse("2026-12-01T00:00:00Z")), "non ancora in vigore")

        // Una taratura in vigore da un mese, poi revocata adesso.
        val running = event.copy(validFrom = OffsetDateTime.now().minusDays(30), validUntil = null)
        service.apply(running.key(), running)
        service.apply(running.key(), null) // tombstone

        assertNull(service.resolve(439041101, 2, OffsetDateTime.now().plusMinutes(1)), "revocata")
        // Prima della revoca la storia resta vera: un campione di dieci giorni fa fu
        // convertito con lei, e una riconversione deve poterlo dire.
        assertNotNull(service.resolve(439041101, 2, OffsetDateTime.now().minusDays(10)))
    }

    @Test
    fun `la piu' recente iniziata vince, e riapplicare non duplica`() {
        service.apply(event.key(), event)
        val renewal =
                event.copy(
                        certificateId = 200,
                        revision = 1,
                        validFrom = OffsetDateTime.parse("2027-11-20T09:00:00Z"),
                        validUntil = OffsetDateTime.parse("2028-11-20T00:00:00Z"),
                )
        service.apply(renewal.key(), renewal)

        assertEquals(128, service.resolve(439041101, 2, OffsetDateTime.parse("2027-06-01T00:00:00Z"))?.record?.certificateId)
        assertEquals(200, service.resolve(439041101, 2, OffsetDateTime.parse("2027-12-01T00:00:00Z"))?.record?.certificateId)
        verify(atLeast = 2) { repository.save(any()) }
        assertTrue(abs(stored.size - 2) == 0, "due tarature, due righe")
    }

    @Test
    fun `chiave del topic`() {
        assertEquals("439041101:2", event.key())
        assertEquals(439041101L to 2, CalibrationActivatedEvent.parseKey("439041101:2"))
        assertNull(CalibrationActivatedEvent.parseKey("0x1A2B"))
    }
}
