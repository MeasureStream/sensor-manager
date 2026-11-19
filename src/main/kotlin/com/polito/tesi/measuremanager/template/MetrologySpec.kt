package com.polito.tesi.measuremanager.template

import java.time.Duration
import java.time.Period
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Il blocco `metrology` di un template, nella forma che serve a calcolare l'incertezza.
 *
 * Lo schema 2.2.0 aggiunge `mpe[]`, `drift`, `influences[]`, `typeAFromVariance` e
 * `requiresCalibration` (specifica § 4.2). I template piu' vecchi non li hanno: per non
 * lasciare senza incertezza ogni sensore pubblicato prima, l'errore massimo si ricava dai
 * campi di Mario che gia' ci sono — `Uncertainty.maxTollerance`, oppure `absUncertainty` in
 * `readingUncertainty` — e lo si dichiara con [mpeSource].
 */
data class MetrologySpec(
        val mpe: List<MpeEntry> = emptyList(),
        /** Da dove viene l'MPE: `mpe` (schema 2.2.0) o il campo storico da cui e' stato dedotto. */
        val mpeSource: String? = null,
        val drift: Drift? = null,
        val influences: List<Influence> = emptyList(),
        val typeAFromVariance: Boolean = false,
        val requiresCalibration: Boolean = true,
        /** La risoluzione nel dominio grezzo, in LSB. Di norma 1. */
        val resolutionLsb: Double = 1.0,
) {
    /** L'MPE che vale per il valore [y], o null se nessun intervallo lo copre. */
    fun mpeAt(y: Double): Double? {
        val entry = mpe.firstOrNull { (it.min == null || y >= it.min) && (it.max == null || y <= it.max) } ?: return null
        return if (entry.relative) abs(y) * entry.value / 100.0 else entry.value
    }

    companion object {
        fun of(metrology: Map<String, Any?>?): MetrologySpec {
            if (metrology == null) return MetrologySpec()

            val declared = listOf(metrology["mpe"]).filterIsInstance<List<*>>().flatten().mapNotNull { mpeEntry(it) }
            val (mpe, source) =
                    when {
                        declared.isNotEmpty() -> declared to "mpe"
                        else -> legacyMpe(metrology)
                    }

            return MetrologySpec(
                    mpe = mpe,
                    mpeSource = source,
                    drift = drift(metrology["drift"]),
                    influences = listOf(metrology["influences"]).filterIsInstance<List<*>>().flatten().mapNotNull { influence(it) },
                    typeAFromVariance = metrology["typeAFromVariance"] as? Boolean ?: false,
                    requiresCalibration = metrology["requiresCalibration"] as? Boolean ?: true,
                    resolutionLsb = resolution(metrology) ?: 1.0,
            )
        }

        private fun number(value: Any?): Double? =
                when (value) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                }

        private fun mpeEntry(raw: Any?): MpeEntry? {
            val map = raw as? Map<*, *> ?: return null
            val value = number(map["value"]) ?: return null
            return MpeEntry(
                    min = number(map["min"]),
                    max = number(map["max"]),
                    value = value,
                    relative = map["relative"] as? Boolean ?: false,
            )
        }

        /**
         * I template 2.1.0 hanno l'accuratezza di targa in due forme. `maxTollerance` e' la
         * tolleranza che Mario usa nel controllo G; `absUncertainty` uniforme e' l'accuratezza
         * dichiarata dal costruttore. Entrambe sono semiampiezze: per l'incertezza si dividono
         * per radice di 3, come un MPE.
         */
        private fun legacyMpe(metrology: Map<String, Any?>): Pair<List<MpeEntry>, String?> {
            fun find(list: Any?, name: String): Double? =
                    (list as? List<*>)
                            ?.mapNotNull { it as? Map<*, *> }
                            ?.firstOrNull { it["varName"] == name }
                            ?.let { number(it["value"]) }
                            ?.takeIf { it > 0 }

            find(metrology["Uncertainty"], "maxTollerance")?.let {
                return listOf(MpeEntry(value = it)) to "Uncertainty.maxTollerance"
            }
            find(metrology["readingUncertainty"], "absUncertainty")?.let {
                return listOf(MpeEntry(value = it)) to "readingUncertainty.absUncertainty"
            }
            return emptyList<MpeEntry>() to null
        }

        private fun drift(raw: Any?): Drift? {
            val map = raw as? Map<*, *> ?: return null
            val value = number(map["value"]) ?: return null
            val period = (map["period"] as? String)?.let { periodSeconds(it) } ?: return null
            if (period <= 0) return null
            return Drift(value, period)
        }

        /** ISO 8601: `P1Y`, `P6M`, `P30D` o una durata `PT720H`. Un anno vale 365,25 giorni. */
        fun periodSeconds(iso: String): Double? =
                runCatching {
                            if (iso.contains('T')) Duration.parse(iso).seconds.toDouble()
                            else {
                                val p = Period.parse(iso)
                                (p.years * 365.25 + p.months * 30.4375 + p.days) * 86400.0
                            }
                        }
                        .getOrNull()

        /**
         * Un contributo delle condizioni d'uso, ridotto a incertezza tipo. `value` e' la
         * semiampiezza per una distribuzione uniforme o triangolare, l'incertezza estesa per
         * una normale (con il suo `k`), oppure gia' un'incertezza tipo se `pdf` e' `standard`.
         */
        private fun influence(raw: Any?): Influence? {
            val map = raw as? Map<*, *> ?: return null
            val value = number(map["value"]) ?: number(map["u"]) ?: return null
            val pdf = (map["pdf"] ?: map["PDF"]) as? String
            val k = number(map["k"]) ?: number(map["coverageFactor"]) ?: 2.0
            val standard =
                    when (pdf?.lowercase()) {
                        "uniform", "rectangular" -> value / sqrt(3.0)
                        "triangular" -> value / sqrt(6.0)
                        "normal", "gaussian" -> value / k
                        else -> value
                    }
            return Influence(name = (map["name"] ?: map["varName"]) as? String ?: "influenza", u = abs(standard))
        }

        private fun resolution(metrology: Map<String, Any?>): Double? {
            (metrology["resolution"] as? Map<*, *>)?.let { return number(it["value"]) }
            return (metrology["readingUncertainty"] as? List<*>)
                    ?.mapNotNull { it as? Map<*, *> }
                    ?.firstOrNull { it["varName"] == "resolution" }
                    ?.let { number(it["value"]) }
                    ?.takeIf { it > 0 }
        }
    }
}

/** L'errore massimo ammesso in un intervallo del misurando, assoluto o in percento. */
data class MpeEntry(
        val min: Double? = null,
        val max: Double? = null,
        val value: Double,
        val relative: Boolean = false,
)

/** La deriva: [value] nell'unita' del misurando ogni [periodSeconds]. */
data class Drift(val value: Double, val periodSeconds: Double) {
    fun ratePerSecond(): Double = value / periodSeconds
}

/** Un contributo delle condizioni d'uso, gia' come incertezza tipo. */
data class Influence(val name: String, val u: Double)
