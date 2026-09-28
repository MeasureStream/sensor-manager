package com.polito.tesi.measuremanager.unitTests.template

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.polito.tesi.measuremanager.template.FormulaError
import com.polito.tesi.measuremanager.template.FormulaEvaluator
import com.polito.tesi.measuremanager.template.SensorConversion
import com.polito.tesi.measuremanager.template.SensorTemplate
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.assertThrows

/**
 * Le conversioni non sono piu' nel codice: stanno nei template. Questo test le verifica
 * contro i valori dell'esempio del capitolo 4.6 della documentazione v1.2, che e' l'unica
 * fonte indipendente da entrambi.
 */
class SensorConversionTest {

    private val conversion = SensorConversion()

    private val mapper =
            ObjectMapper()
                    .registerKotlinModule()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /** I template veri del repository accanto. Se non c'e', il test si salta invece di fallire. */
    private fun template(file: String): SensorTemplate {
        val path = File("../sensor-templates/sensors/$file")
        assumeTrue(path.exists(), "Repository sensor-templates non accanto: test saltato")
        return mapper.readValue(path, SensorTemplate::class.java)
    }

    private fun assertClose(expected: Double, actual: Double?, tolerance: Double, what: String) {
        assertNotNull(actual, "$what: nessun valore")
        assertTrue(
                abs(expected - actual) <= tolerance,
                "$what: atteso ~$expected, ottenuto $actual",
        )
    }

    /* ----------------------------------------------------------- formule */

    @Test
    fun `precedenza e potenza`() {
        assertEquals(7.0, FormulaEvaluator.evaluate("1 + 2 * 3", 0.0, emptyList()))
        assertEquals(9.0, FormulaEvaluator.evaluate("(1 + 2) * 3", 0.0, emptyList()))
        assertEquals(8.0, FormulaEvaluator.evaluate("2 ** 3", 0.0, emptyList()))
        assertEquals(-8.0, FormulaEvaluator.evaluate("-2 ** 3", 0.0, emptyList()))
        assertEquals(0.5, FormulaEvaluator.evaluate("1 / 2", 0.0, emptyList()))
    }

    @Test
    fun `variabile e coefficienti`() {
        assertEquals(11.0, FormulaEvaluator.evaluate("c[0] + c[1] * x", 5.0, listOf(1.0, 2.0)))
        assertEquals(1.5e-3, FormulaEvaluator.evaluate("1.5e-3", 0.0, emptyList()))
    }

    @Test
    fun `la grammatica e' chiusa`() {
        // Niente variabili non dichiarate, niente funzioni fuori dall'elenco: un template
        // arriva da fuori e non deve poter eseguire nulla.
        assertThrows<FormulaError> { FormulaEvaluator.evaluate("System.exit(0)", 0.0, emptyList()) }
        assertThrows<FormulaError> { FormulaEvaluator.evaluate("pippo(2)", 0.0, emptyList()) }
        assertThrows<FormulaError> { FormulaEvaluator.evaluate("y + 1", 0.0, emptyList()) }
        assertThrows<FormulaError> { FormulaEvaluator.evaluate("(1 + 2", 0.0, emptyList()) }
    }

    @Test
    fun `risultato non finito diventa null`() {
        // ln di un negativo, divisione per zero: il valore non c'e', ma non e' un errore di
        // formula — la lettura era fuori dal dominio.
        assertEquals(null, FormulaEvaluator.evaluate("ln(x)", -1.0, emptyList()))
        assertEquals(null, FormulaEvaluator.evaluate("1 / x", 0.0, emptyList()))
    }

    /* ------------------------------------------------- template reali */

    @Test
    fun `NTC - 1370 sono 24,5 gradi`() {
        val ntc = template("ntc_temperature.json")
        val result = conversion.toPhysical(ntc, 1370.0)
        assertTrue(result.converted, "la conversione non e' stata applicata")
        assertClose(24.54, result.value, 0.2, "NTC media")
    }

    @Test
    fun `NTC - la varianza si propaga con la derivata locale`() {
        val ntc = template("ntc_temperature.json")
        val result = conversion.varianceToPhysical(ntc, 1370.0, 4.0)
        assertTrue(result.converted)
        // Attorno a 1370 la sensibilita' e' circa 0,023 K per LSB: 4 * 0,023^2 ~ 0,0022.
        assertClose(0.0022, result.value, 0.0005, "NTC varianza")
    }

    @Test
    fun `pressione - 10134 sono 1013,4 mbar`() {
        val result = conversion.toPhysical(template("pressure_ms5837.json"), 10134.0)
        assertTrue(result.converted)
        assertClose(1013.4, result.value, 0.01, "pressione")
    }

    @Test
    fun `umidita - 29495 sono 50,3 percento`() {
        val result = conversion.toPhysical(template("humidity_hpp845e.json"), 29495.0)
        assertTrue(result.converted)
        assertClose(50.3, result.value, 0.1, "umidita'")
    }

    @Test
    fun `accelerometro - zero resta zero`() {
        val result = conversion.toPhysical(template("accelerometer_lsm6dsm.json"), 0.0)
        assertTrue(result.converted)
        assertClose(0.0, result.value, 1e-9, "accelerometro")
    }

    @Test
    fun `senza template il valore resta grezzo`() {
        val result = conversion.toPhysical(null, 1370.0)
        assertEquals(1370.0, result.value)
        assertTrue(!result.converted, "un valore senza template non puo' dirsi convertito")
    }

    @Test
    fun `i template dichiarano byte ed encoding di ogni metrica`() {
        val ntc = template("ntc_temperature.json")
        val mean = ntc.supportedMetrics?.firstOrNull { it.name == "mean" }
        assertNotNull(mean, "supportedMetrics assente: il decoder non saprebbe quanti byte leggere")
        assertEquals(2, mean.bytes)
        assertEquals("u16", mean.encoding)
        assertEquals("calibration", mean.transform)
    }
}
