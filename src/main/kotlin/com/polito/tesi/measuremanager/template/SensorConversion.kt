package com.polito.tesi.measuremanager.template

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Esito di una conversione: il valore e se e' davvero una grandezza fisica. */
data class ConvertedValue(val value: Double?, val converted: Boolean)

/**
 * Una funzione grezzo → grandezza fisica: formula, coefficienti in ordine di `id`, variabile.
 *
 * Ha la forma del blocco `calibration` di un template, ed e' la stessa forma con cui
 * metrology-service consegna la funzione di un esemplare tarato: chi converte non deve sapere
 * da dove viene.
 */
data class ConversionFunction(
        val formula: String,
        val coefficients: List<Double>,
        val input: String = "x",
) {
    fun evaluate(x: Double, c: List<Double> = coefficients): Double? =
            FormulaEvaluator.evaluate(formula, x, c, input)

    /**
     * La derivata locale df/dx, per differenze centrali su un LSB: e' anche il passo piu'
     * piccolo che il convertitore sa distinguere, quindi non ha senso stimarla piu' fine.
     */
    fun derivative(x: Double): Double? {
        val above = evaluate(x + 1) ?: return null
        val below = evaluate(x - 1) ?: return null
        return (above - below) / 2.0
    }

    /**
     * Il gradiente rispetto ai coefficienti, per differenze centrali. Il passo e' relativo
     * al coefficiente: fra il termine noto di un NTC (~1e-3) e quello cubico (~1e-9) ci sono
     * sei ordini di grandezza, e un passo assoluto unico sarebbe sbagliato per tutti.
     */
    fun gradient(x: Double): List<Double>? =
            coefficients.indices.map { i ->
                val ci = coefficients[i]
                val h = if (ci != 0.0) kotlin.math.abs(ci) * 1e-6 else 1e-12
                val up = coefficients.toMutableList().also { it[i] = ci + h }
                val down = coefficients.toMutableList().also { it[i] = ci - h }
                val fUp = evaluate(x, up) ?: return null
                val fDown = evaluate(x, down) ?: return null
                (fUp - fDown) / (2 * h)
            }

    companion object {
        /**
         * Legge un blocco `calibration`: la formula sta sotto una chiave omonima a `type`.
         * `type: "none"` e i blocchi senza formula restituiscono null.
         */
        fun of(calibration: Map<String, Any?>?): ConversionFunction? {
            if (calibration == null) return null
            val type = calibration["type"] as? String ?: return null
            if (type == "none") return null
            val formula = calibration[type] as? String ?: return null
            return ConversionFunction(formula, coefficientsOf(calibration), inputOf(calibration))
        }

        /** I coefficienti in ordine di `id`; la vecchia forma piatta resta letta come ripiego. */
        private fun coefficientsOf(calibration: Map<String, Any?>): List<Double> {
            @Suppress("UNCHECKED_CAST")
            val declared = calibration["c"] as? List<Map<String, Any?>>
            if (declared != null) {
                return declared
                        .sortedBy { (it["id"] as? Number)?.toInt() ?: 0 }
                        .map { (it["value"] as? Number)?.toDouble() ?: 0.0 }
            }

            @Suppress("UNCHECKED_CAST")
            val flat = calibration["coefficients"] as? List<Number>
            return flat?.map { it.toDouble() } ?: emptyList()
        }

        private fun inputOf(calibration: Map<String, Any?>): String {
            @Suppress("UNCHECKED_CAST")
            val input = calibration["input"] as? List<String>
            return input?.firstOrNull() ?: "x"
        }
    }
}

/**
 * Applica la taratura: quella dell'esemplare se c'e', altrimenti quella dichiarata dal
 * template del modello.
 *
 * Le formule non sono nel codice: stanno in `calibration` del template, o nella funzione che
 * un certificato ha attivato per quel sensore. Questo servizio le valuta con lo stesso
 * valutatore a grammatica chiusa, qualunque sia la provenienza.
 */
@Service
class SensorConversion {
    private val logger = LoggerFactory.getLogger(SensorConversion::class.java)

    /**
     * Grezzo → grandezza fisica.
     *
     * [instance] e' la funzione dell'esemplare tarato: se c'e', vince sul template. Senza,
     * vale la formula nominale del modello. Il valore resta non convertito quando nessuna
     * delle due dichiara una formula, o quando il template dice `outputFormat: calibrated`
     * perche' il dispositivo tara gia' a bordo.
     */
    fun toPhysical(
            template: SensorTemplate?,
            raw: Double,
            instance: ConversionFunction? = null,
    ): ConvertedValue {
        if (instance == null && template?.outputFormat == "calibrated") return ConvertedValue(raw, true)

        val function = instance ?: functionOf(template) ?: return ConvertedValue(raw, false)

        return try {
            val value = function.evaluate(raw)
            if (value == null) ConvertedValue(null, false) else ConvertedValue(value, true)
        } catch (e: FormulaError) {
            // Una formula rotta non deve fermare la decodifica del report: il valore resta
            // grezzo e il motivo finisce nei log.
            logger.warn("Formula non valutabile per {}: {}", template?.modelName, e.message)
            ConvertedValue(raw, false)
        }
    }

    /**
     * Propaga la varianza attraverso la conversione.
     *
     * Per una formula lineare basterebbe il quadrato del coefficiente, ma per una curva come
     * quella dell'NTC no: la sensibilita' cambia lungo la scala. Si usa quindi la derivata
     * **locale**, stimata attorno al valore misurato su un LSB.
     *
     * `var_fisica = var_grezza * (df/dx)^2`
     */
    fun varianceToPhysical(
            template: SensorTemplate?,
            rawMean: Double,
            rawVariance: Double,
            instance: ConversionFunction? = null,
    ): ConvertedValue {
        val function = instance ?: functionOf(template) ?: return ConvertedValue(rawVariance, false)

        return try {
            val derivative = function.derivative(rawMean) ?: return ConvertedValue(rawVariance, false)
            ConvertedValue(rawVariance * derivative * derivative, true)
        } catch (e: FormulaError) {
            logger.warn("Varianza non propagabile per {}: {}", template?.modelName, e.message)
            ConvertedValue(rawVariance, false)
        }
    }

    /** La funzione nominale del modello, o null se il template non ne dichiara una. */
    fun functionOf(template: SensorTemplate?): ConversionFunction? = ConversionFunction.of(template?.calibration)
}
