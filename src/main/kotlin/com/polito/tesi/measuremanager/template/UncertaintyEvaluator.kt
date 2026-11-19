package com.polito.tesi.measuremanager.template

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.sqrt
import org.springframework.stereotype.Component

/** Tutto cio' che serve a stimare l'incertezza di un valore convertito. */
data class UncertaintyInput(
        /** La funzione usata per convertire; null se il dispositivo trasmette gia' il fisico. */
        val function: ConversionFunction?,
        /** Vero se [function] e' la taratura dell'esemplare, falso se e' quella nominale. */
        val instance: Boolean,
        /** Covarianza dei coefficienti della taratura dell'esemplare. */
        val covariance: List<List<Double>>? = null,
        val raw: Double,
        val value: Double,
        val spec: MetrologySpec,
        /** Varianza grezza trasmessa, per il tipo A della media. */
        val rawVariance: Double? = null,
        /** Quanti campioni compongono la statistica; null se non si sa. */
        val samplesInWindow: Double? = null,
        /** Da quando vale la taratura dell'esemplare: l'origine della deriva. */
        val calibratedSince: OffsetDateTime? = null,
        val at: OffsetDateTime,
)

/** Incertezza tipo e contributi, tutti nell'unita' del misurando e con k = 1. */
data class UncertaintyBudget(val uStd: Double, val components: Map<String, Double>)

/**
 * L'incertezza di un valore convertito (specifica § 4.2), come budget GUM a contributi
 * indipendenti sommati in quadratura.
 *
 * - **taratura**: con la taratura dell'esemplare `gᵀ·Σc·g`, il gradiente della funzione
 *   rispetto ai coefficienti per la loro covarianza; senza, l'MPE del modello diviso per
 *   radice di 3 (distribuzione uniforme sulla tolleranza di targa);
 * - **risoluzione**: `|df/dx| · q/√12`, con q un LSB del valore grezzo;
 * - **tipo A**: per la media, `|df/dx| · √(s²/n)` dalla varianza trasmessa, solo se il
 *   template lo consente (`typeAFromVariance`) — per una temperatura ambiente la varianza e'
 *   la variabilita' del misurando, non del sensore;
 * - **deriva**: `r · Δt/√3` dalla data della taratura dell'esemplare;
 * - **influenze**: i contributi dichiarati nel template.
 *
 * Restituisce null quando manca il contributo di taratura: un'incertezza senza la sua
 * componente principale sarebbe un numero piu' piccolo del vero, cioe' una falsa precisione.
 */
@Component
class UncertaintyEvaluator {

    fun evaluate(input: UncertaintyInput): UncertaintyBudget? {
        // Un dispositivo che tara a bordo trasmette gia' il fisico: la sensibilita' e' 1.
        val dfdx: Double =
                when (val function = input.function) {
                    null -> 1.0
                    else -> runCatching { function.derivative(input.raw) }.getOrNull() ?: return null
                }
        val components = linkedMapOf<String, Double>()

        components[CALIBRATION] = calibrationTerm(input) ?: return null
        components[RESOLUTION] = abs(dfdx) * input.spec.resolutionLsb / sqrt(12.0)

        if (input.spec.typeAFromVariance && input.rawVariance != null && input.rawVariance >= 0) {
            val n = input.samplesInWindow?.takeIf { it >= 1 } ?: 1.0
            components[TYPE_A] = abs(dfdx) * sqrt(input.rawVariance / n)
        }

        val drift = input.spec.drift
        if (drift != null && input.instance && input.calibratedSince != null) {
            val elapsed = Duration.between(input.calibratedSince, input.at).seconds.coerceAtLeast(0)
            components[DRIFT] = abs(drift.ratePerSecond()) * elapsed / sqrt(3.0)
        }

        if (input.spec.influences.isNotEmpty()) {
            components[INFLUENCES] = sqrt(input.spec.influences.sumOf { it.u * it.u })
        }

        val u = sqrt(components.values.sumOf { it * it })
        return if (u.isFinite()) UncertaintyBudget(u, components) else null
    }

    private fun calibrationTerm(input: UncertaintyInput): Double? {
        val function = input.function
        val covariance = input.covariance
        if (input.instance && function != null && covariance != null && covariance.size == function.coefficients.size) {
            val g = runCatching { function.gradient(input.raw) }.getOrNull()
            if (g != null) {
                var variance = 0.0
                for (i in g.indices) for (j in g.indices) variance += g[i] * covariance[i][j] * g[j]
                if (variance >= 0 && variance.isFinite()) return sqrt(variance)
            }
        }
        // Senza covarianza dell'esemplare vale la tolleranza del modello: e' cio' che il
        // costruttore garantisce, e un certificato senza covarianza non dice di meglio.
        return input.spec.mpeAt(input.value)?.let { it / sqrt(3.0) }
    }

    companion object {
        const val CALIBRATION = "calibration"
        const val RESOLUTION = "resolution"
        const val TYPE_A = "typeA"
        const val DRIFT = "drift"
        const val INFLUENCES = "influences"
    }
}
