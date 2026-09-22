package com.polito.tesi.measuremanager.template

/**
 * Vista tipizzata di un documento `kind: protocol`.
 *
 * Porta le tabelle che il server usa davvero; il resto del dizionario (sentinelle, bit di
 * stato, codici evento) resta nel documento e viaggia verso l'interfaccia cosi' com'e'.
 */
data class ProtocolDictionary(
        val templateVersion: String? = null,
        val periods: Map<String, PeriodScale> = emptyMap(),
) {
    val sampling: PeriodScale?
        get() = periods["sampling"]

    val transmission: PeriodScale?
        get() = periods["transmission"]
}

/**
 * Una scala non lineare indice → tempo. Sul filo viaggia l'indice su un byte; il tempo si
 * ricava dal segmento in cui l'indice cade, con `base + (idx - from) * step`.
 */
data class PeriodScale(
        val unit: String = "second",
        val off: Int = 0,
        val minIndex: Int = 0,
        val maxIndex: Int = 255,
        val ticks: List<Int> = emptyList(),
        val segments: List<PeriodSegment> = emptyList(),
) {
    /** Il periodo in secondi, o null se l'indice non cade in nessun segmento. */
    fun seconds(index: Int): Double? {
        if (index == off) return 0.0
        val segment = segments.firstOrNull { index >= it.from && index <= it.to } ?: return null
        return segment.base + (index - segment.from) * segment.step
    }
}

data class PeriodSegment(
        val from: Int = 0,
        val to: Int = 0,
        val base: Double = 0.0,
        val step: Double = 0.0,
)
