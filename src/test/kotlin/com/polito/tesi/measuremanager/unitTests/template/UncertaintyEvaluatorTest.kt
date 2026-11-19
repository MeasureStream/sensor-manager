package com.polito.tesi.measuremanager.unitTests.template

import com.polito.tesi.measuremanager.template.ConversionFunction
import com.polito.tesi.measuremanager.template.Drift
import com.polito.tesi.measuremanager.template.MetrologySpec
import com.polito.tesi.measuremanager.template.MpeEntry
import com.polito.tesi.measuremanager.template.UncertaintyEvaluator
import com.polito.tesi.measuremanager.template.UncertaintyInput
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L'incertezza di un valore contro un budget GUM fatto a mano (prova di accettazione di I2).
 *
 * La funzione e' lineare, `y = c0 + c1·x`, cosi' ogni contributo si scrive in una riga e
 * si verifica senza fidarsi del codice che lo calcola:
 *
 * | contributo  | formula                     | valore                        |
 * |-------------|-----------------------------|-------------------------------|
 * | taratura    | √(σ0² + x²·σ1² + 2x·σ01)    | √(0.04 + 30000²·1e-10) = √0.13 |
 * | risoluzione | c1 / √12                    | 0.0019073486328125 / √12       |
 * | deriva      | 0.5 %RH/anno · ½ anno / √3  | 0.25 / √3                      |
 *
 * u = √(0.13 + (c1/√12)² + (0.25/√3)²) = 0.388373…
 */
class UncertaintyEvaluatorTest {

    private val evaluator = UncertaintyEvaluator()
    private val c1 = 0.0019073486328125
    private val linear = ConversionFunction("c[0] + c[1] * x", listOf(-6.0, c1))
    private val now = OffsetDateTime.parse("2027-06-02T12:00:00Z")

    private fun close(expected: Double, actual: Double?, tolerance: Double = 1e-9, what: String = "") {
        assertNotNull(actual, "$what: nessun valore")
        assertTrue(abs(expected - actual) <= tolerance, "$what: atteso $expected, ottenuto $actual")
    }

    @Test
    fun `budget con la taratura dell'esemplare, a mano`() {
        val year = MetrologySpec.periodSeconds("P1Y")!!
        val budget =
                evaluator.evaluate(
                        UncertaintyInput(
                                function = linear,
                                instance = true,
                                covariance = listOf(listOf(0.04, 0.0), listOf(0.0, 1e-10)),
                                raw = 30000.0,
                                value = -6.0 + 30000 * c1,
                                spec = MetrologySpec(drift = Drift(0.5, year)),
                                calibratedSince = now.minusSeconds((year / 2).toLong()),
                                at = now,
                        )
                )

        assertNotNull(budget)
        close(sqrt(0.13), budget.components["calibration"], 1e-9, "taratura")
        close(c1 / sqrt(12.0), budget.components["resolution"], 1e-12, "risoluzione")
        close(0.25 / sqrt(3.0), budget.components["drift"], 1e-6, "deriva")
        close(0.388373, budget.uStd, 1e-6, "incertezza tipo")
    }

    @Test
    fun `la covarianza fra i coefficienti entra con il suo segno`() {
        // σ01 = -6e-7: con x = 30000 il termine incrociato vale 2·30000·(-6e-7) = -0.036.
        val budget =
                evaluator.evaluate(
                        UncertaintyInput(
                                function = linear,
                                instance = true,
                                covariance = listOf(listOf(0.04, -6e-7), listOf(-6e-7, 1e-10)),
                                raw = 30000.0,
                                value = 51.2,
                                spec = MetrologySpec(),
                                at = now,
                        )
                )
        close(sqrt(0.04 + 0.09 - 0.036), budget?.components?.get("calibration"), 1e-9, "taratura")
    }

    @Test
    fun `senza taratura dell'esemplare vale MPE diviso radice di tre`() {
        val budget =
                evaluator.evaluate(
                        UncertaintyInput(
                                function = linear,
                                instance = false,
                                raw = 30000.0,
                                value = 51.2,
                                spec = MetrologySpec(mpe = listOf(MpeEntry(value = 2.0))),
                                at = now,
                        )
                )
        close(2.0 / sqrt(3.0), budget?.components?.get("calibration"), 1e-12, "MPE/√3")
        assertNull(budget?.components?.get("drift"), "senza taratura dell'esemplare la deriva non ha un'origine")
    }

    @Test
    fun `MPE relativo e per intervallo`() {
        val spec =
                MetrologySpec(
                        mpe =
                                listOf(
                                        MpeEntry(min = 0.0, max = 20.0, value = 3.0),
                                        MpeEntry(min = 20.0, max = 80.0, value = 2.0, relative = true),
                                )
                )
        assertEquals(3.0, spec.mpeAt(10.0))
        close(1.0, spec.mpeAt(50.0), 1e-12, "2 % di 50")
        assertNull(spec.mpeAt(90.0), "fuori da ogni intervallo non si inventa un MPE")
    }

    @Test
    fun `senza MPE ne' covarianza non si dichiara un'incertezza`() {
        val budget =
                evaluator.evaluate(
                        UncertaintyInput(
                                function = linear,
                                instance = false,
                                raw = 30000.0,
                                value = 51.2,
                                spec = MetrologySpec(),
                                at = now,
                        )
                )
        assertNull(budget, "un budget senza il contributo di taratura sarebbe una falsa precisione")
    }

    @Test
    fun `tipo A dalla varianza solo se il template lo consente`() {
        val base =
                UncertaintyInput(
                        function = linear,
                        instance = false,
                        raw = 30000.0,
                        value = 51.2,
                        spec = MetrologySpec(mpe = listOf(MpeEntry(value = 2.0))),
                        rawVariance = 16.0,
                        samplesInWindow = 4.0,
                        at = now,
                )
        assertNull(evaluator.evaluate(base)?.components?.get("typeA"))

        val allowed = evaluator.evaluate(base.copy(spec = base.spec.copy(typeAFromVariance = true)))
        // s = 4 LSB, n = 4: la media ha s/√n = 2 LSB, cioe' 2·c1 nell'unita' del misurando.
        close(2 * c1, allowed?.components?.get("typeA"), 1e-12, "tipo A")
    }

    @Test
    fun `i template 2_1_0 ricavano l'MPE dai campi di Mario`() {
        val ntc =
                MetrologySpec.of(
                        mapOf(
                                "Uncertainty" to listOf(mapOf("varName" to "maxTollerance", "value" to 0.5)),
                                "readingUncertainty" to listOf(mapOf("varName" to "resolution", "value" to 1)),
                        )
                )
        assertEquals(0.5, ntc.mpeAt(20.0))
        assertEquals("Uncertainty.maxTollerance", ntc.mpeSource)

        val humidity =
                MetrologySpec.of(
                        mapOf(
                                "readingUncertainty" to
                                        listOf(mapOf("varName" to "absUncertainty", "value" to 5.0, "PDF" to "uniform"))
                        )
                )
        assertEquals(5.0, humidity.mpeAt(50.0))
        assertTrue(humidity.requiresCalibration, "senza il campo, un sensore entra nel perimetro")
    }

    @Test
    fun `schema 2_2_0, campi nuovi`() {
        val spec =
                MetrologySpec.of(
                        mapOf(
                                "mpe" to listOf(mapOf("min" to 20, "max" to 80, "value" to 2.0)),
                                "drift" to mapOf("value" to 0.5, "period" to "P1Y"),
                                "influences" to listOf(mapOf("name" to "autoriscaldamento", "value" to 0.3, "pdf" to "uniform")),
                                "typeAFromVariance" to true,
                                "requiresCalibration" to false,
                        )
                )
        assertEquals("mpe", spec.mpeSource)
        assertEquals(2.0, spec.mpeAt(50.0))
        close(0.5 / (365.25 * 86400), spec.drift?.ratePerSecond(), 1e-18, "deriva al secondo")
        close(0.3 / sqrt(3.0), spec.influences.single().u, 1e-12, "influenza uniforme")
        assertTrue(spec.typeAFromVariance)
        assertEquals(false, spec.requiresCalibration)
    }
}
