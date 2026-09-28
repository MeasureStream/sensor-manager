package com.polito.tesi.measuremanager.template

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Esito di una conversione: il valore e se e' davvero una grandezza fisica. */
data class ConvertedValue(val value: Double?, val converted: Boolean)

/**
 * Applica la taratura dichiarata dal template.
 *
 * Le formule non sono piu' nel codice: stanno in `calibration` del template del sensore, e
 * questo servizio le valuta. Aggiungere un sensore nuovo diventa pubblicare un documento;
 * correggere una formula sbagliata diventa pubblicare una versione, senza ricompilare.
 */
@Service
class SensorConversion {
    private val logger = LoggerFactory.getLogger(SensorConversion::class.java)

    /**
     * Grezzo → grandezza fisica.
     *
     * Restituisce il valore non convertito quando il template non dichiara una formula, o
     * quando dichiara `outputFormat: calibrated` perche' il dispositivo tara gia' a bordo.
     */
    fun toPhysical(template: SensorTemplate?, raw: Double): ConvertedValue {
        if (template?.outputFormat == "calibrated") return ConvertedValue(raw, true)

        val formula = formulaOf(template) ?: return ConvertedValue(raw, false)
        val coefficients = coefficientsOf(template)

        return try {
            val value = FormulaEvaluator.evaluate(formula, raw, coefficients, inputOf(template))
            if (value == null) ConvertedValue(null, false) else ConvertedValue(value, true)
        } catch (e: FormulaError) {
            // Un template con una formula rotta non deve fermare la decodifica del report:
            // il valore resta grezzo e il motivo finisce nei log.
            logger.warn("Formula non valutabile per {}: {}", template?.modelName, e.message)
            ConvertedValue(raw, false)
        }
    }

    /**
     * Propaga la varianza attraverso la conversione.
     *
     * Per una formula lineare basterebbe il quadrato del coefficiente, ma per una curva come
     * quella dell'NTC no: la sensibilita' cambia lungo la scala. Si usa quindi la derivata
     * **locale**, stimata attorno al valore misurato su un LSB — che e' anche il passo piu'
     * piccolo che il convertitore sa distinguere, quindi non ha senso stimarla piu' fine.
     *
     * `var_fisica = var_grezza * (df/dx)^2`
     */
    fun varianceToPhysical(
            template: SensorTemplate?,
            rawMean: Double,
            rawVariance: Double,
    ): ConvertedValue {
        val formula = formulaOf(template) ?: return ConvertedValue(rawVariance, false)
        val coefficients = coefficientsOf(template)
        val variable = inputOf(template)

        return try {
            val above = FormulaEvaluator.evaluate(formula, rawMean + 1, coefficients, variable)
            val below = FormulaEvaluator.evaluate(formula, rawMean - 1, coefficients, variable)
            if (above == null || below == null) return ConvertedValue(rawVariance, false)

            val derivative = (above - below) / 2.0
            ConvertedValue(rawVariance * derivative * derivative, true)
        } catch (e: FormulaError) {
            logger.warn("Varianza non propagabile per {}: {}", template?.modelName, e.message)
            ConvertedValue(rawVariance, false)
        }
    }

    /**
     * La formula dichiarata: sta sotto una chiave omonima a `calibration.type`.
     * `type: "none"` e i template senza taratura restituiscono null, e il valore resta grezzo.
     */
    private fun formulaOf(template: SensorTemplate?): String? {
        val calibration = template?.calibration ?: return null
        val type = calibration["type"] as? String ?: return null
        if (type == "none") return null
        return calibration[type] as? String
    }

    /** I coefficienti in ordine di `id`; la vecchia forma piatta resta letta come ripiego. */
    private fun coefficientsOf(template: SensorTemplate?): List<Double> {
        val calibration = template?.calibration ?: return emptyList()

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

    /** Il nome della variabile d'ingresso, di norma `x`. */
    private fun inputOf(template: SensorTemplate?): String {
        @Suppress("UNCHECKED_CAST")
        val input = template?.calibration?.get("input") as? List<String>
        return input?.firstOrNull() ?: "x"
    }
}
