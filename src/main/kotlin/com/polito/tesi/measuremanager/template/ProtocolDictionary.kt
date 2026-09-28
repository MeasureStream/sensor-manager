package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Vista tipizzata di un documento `kind: protocol`.
 *
 * Porta le tabelle che il server usa davvero; il resto del dizionario viaggia verso
 * l'interfaccia cosi' com'e'. Ogni campo ha un default, quindi un dizionario piu' vecchio di
 * questo codice si legge lo stesso: le tabelle che non dichiara restano vuote e chi le usa
 * ricade sul proprio comportamento di sicurezza.
 */
data class ProtocolDictionary(
        val templateVersion: String? = null,
        val periods: Map<String, PeriodScale> = emptyMap(),
        /** Ordine canonico delle metriche: l'ordine in cui i campi viaggiano nel report. */
        val metrics: List<MetricEntry> = emptyList(),
        /** Quale bit del byte CONTENT accende quale classe di metriche. */
        val contentClasses: List<ContentClass> = emptyList(),
        val fragmentation: Fragmentation = Fragmentation(),
        /** I sedici bit della parola di stato della CU (poll 0x0B). */
        val statusBits: List<StatusBit> = emptyList(),
        /** I sedici bit della parola di stato di una MU (comando 0x12). */
        val muStatusBits: List<StatusBit> = emptyList(),
        /** Valori che non sono misure ma stati, per ambito: `battery`, `metric`. */
        val sentinels: Map<String, List<SentinelEntry>> = emptyMap(),
) {
    val sampling: PeriodScale?
        get() = periods["sampling"]

    val transmission: PeriodScale?
        get() = periods["transmission"]
}

/**
 * Una metrica nell'ordine canonico: quanti byte occupa, in quale classe di cadenza viaggia e
 * quale bit la abilita nella `Stat bitmap` della 0x23.
 */
data class MetricEntry(
        val id: Int = 0,
        val bit: Int = 0,
        val name: String = "",
        val bytes: Int = 2,
        /** BASE, EXTENDED o RARE: e' una cadenza di trasmissione, non una categoria. */
        @JsonProperty("class") val metricClass: String = "BASE",
        val label: String? = null,
        val unit: String? = null,
        val description: String? = null,
)

data class ContentClass(
        val bit: Int = 0,
        @JsonProperty("class") val metricClass: String = "",
)

/**
 * Regole del byte FRAG. Il protocollo non porta un identificatore di report, quindi la
 * regola di ricomposizione va scritta da qualche parte: e' qui, non nel codice.
 */
data class Fragmentation(
        /** Bit che marca l'ultimo frammento. */
        val lastBit: Int = 7,
        /** Maschera dell'indice del frammento. */
        val indexMask: Int = 0x7F,
        /** Un frammento con indice 0 ricomincia l'accumulo, qualunque cosa fosse aperta. */
        val resetOnIndexZero: Boolean = true,
        /** Dopo quanto un report incompleto si considera perso. */
        val timeoutSeconds: Long = 900,
)

/**
 * Un bit di una parola di stato. [kind] distingue gli stati, che si ricalcolano e si spengono
 * da soli, dagli eventi, che restano latchati finche' il server non li conferma; [origin] dice
 * chi ha alzato il bit, la MU o la CU.
 */
data class StatusBit(
        val bit: Int = 0,
        val kind: String? = null,
        val origin: String? = null,
        val meaning: String = "",
        val description: String = "",
)

/**
 * Un valore che non e' un dato ma uno stato: la batteria a 254 non e' il 254% di carica.
 */
data class SentinelEntry(
        val value: Long = 0,
        val meaning: String = "",
        val appliesTo: String? = null,
        val description: String? = null,
)

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
