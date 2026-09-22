package com.polito.tesi.measuremanager.template

import com.polito.tesi.measuremanager.entities.Metric
import java.nio.ByteBuffer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Una metrica letta dal report, prima di diventare una riga di database. */
data class DecodedMetric(
        val metric: String,
        val raw: Double,
        val value: Double?,
        val converted: Boolean,
        /** Media e varianza a 0xFFFF: nessun dato nuovo nell'intervallo. */
        val noNewData: Boolean = false,
)

/** Il report non si puo' leggere: byte mancanti o metrica non descritta. */
class ReportTruncated(message: String) : Exception(message)

/**
 * Legge le metriche di uno slot dal report, usando il template del sensore.
 *
 * Quanti byte occupa una metrica, se ha segno e come si converte non stanno piu' nel codice:
 * stanno in `supportedMetrics` del template. Un sensore nuovo si aggiunge pubblicando un
 * documento, e una metrica in piu' non richiede di toccare il decoder.
 *
 * Quali metriche siano attive lo dice ancora `configurationMeasure` sul sensore: arrivera'
 * dallo snapshot CFG_VER con il passo successivo, e a quel punto sparisce anche quello.
 */
@Service
class ReportDecoder(
        private val conversion: SensorConversion,
) {
    private val logger = LoggerFactory.getLogger(ReportDecoder::class.java)

    /** Sentinella del protocollo: media e varianza entrambe a questo valore. */
    private val noNewDataSentinel = 0xFFFF

    /**
     * Le metriche attese per uno slot, nell'ordine in cui viaggiano.
     * Mappa la vecchia stringa di configurazione sui nomi dell'ordine canonico.
     */
    fun expectedMetrics(configurationMeasure: String?): List<String> =
            when (configurationMeasure?.lowercase()) {
                "avg-std", "average-std", null -> listOf(Metric.MEAN, Metric.VARIANCE)
                "max-min" -> listOf(Metric.MAX, Metric.MIN)
                "integral" -> listOf(Metric.INTEGRAL)
                "puntual", "punctual" -> listOf(Metric.PUNCTUAL)
                else -> listOf(Metric.MEAN, Metric.VARIANCE)
            }

    /**
     * Legge dal buffer le metriche di uno slot e le converte.
     * Solleva [ReportTruncated] se i byte non bastano: il report va scartato per intero,
     * perche' da quel punto in poi ogni lettura sarebbe disallineata.
     */
    fun decodeSlot(
            buffer: ByteBuffer,
            template: SensorTemplate?,
            metrics: List<String>,
            slotLabel: String,
    ): List<DecodedMetric> {
        val raws = LinkedHashMap<String, Double>()

        for (metric in metrics) {
            val spec = specOf(template, metric)
            val size = spec?.bytes ?: 2
            if (buffer.remaining() < size) {
                throw ReportTruncated(
                        "$slotLabel, metrica $metric: servivano $size byte, ne restavano ${buffer.remaining()}"
                )
            }
            raws[metric] = readRaw(buffer, size, spec?.encoding)
        }

        // Sentinella: media e varianza entrambe al massimo significano "nessun dato nuovo",
        // e non vanno salvate come misure. 0x0000 invece e' un valore legittimo.
        val mean = raws[Metric.MEAN]
        val variance = raws[Metric.VARIANCE]
        if (mean != null &&
                        variance != null &&
                        mean.toInt() == noNewDataSentinel &&
                        variance.toInt() == noNewDataSentinel
        ) {
            logger.debug("{}: nessun dato nuovo nell'intervallo", slotLabel)
            return raws.map { (metric, raw) -> DecodedMetric(metric, raw, null, false, true) }
        }

        return raws.map { (metric, raw) -> convert(template, metric, raw, raws) }
    }

    /** Converte una metrica secondo il `transform` dichiarato dal template. */
    private fun convert(
            template: SensorTemplate?,
            metric: String,
            raw: Double,
            all: Map<String, Double>,
    ): DecodedMetric {
        val transform = specOf(template, metric)?.transform ?: defaultTransform(metric)

        val result =
                when (transform) {
                    "calibration" -> conversion.toPhysical(template, raw)
                    "variance" -> {
                        val mean = all[Metric.MEAN]
                        if (mean == null) ConvertedValue(raw, false)
                        else conversion.varianceToPhysical(template, mean, raw)
                    }
                    // L'integrale di una grandezza non lineare non ha una conversione esatta,
                    // e lo ZCR e' gia' una frequenza: restano come arrivano.
                    else -> ConvertedValue(raw, false)
                }

        return DecodedMetric(metric, raw, result.value, result.converted)
    }

    /** Se il template non dichiara `transform`, si deduce dal nome della metrica. */
    private fun defaultTransform(metric: String): String =
            when (metric) {
                Metric.MEAN, Metric.MAX, Metric.MIN, Metric.MEDIAN, Metric.P_HIGH, Metric.P_LOW,
                Metric.PUNCTUAL -> "calibration"
                Metric.VARIANCE -> "variance"
                else -> "none"
            }

    private fun specOf(template: SensorTemplate?, metric: String): MetricSpec? =
            template?.supportedMetrics?.firstOrNull { it.name.equals(metric, ignoreCase = true) }

    /** Legge il campo rispettando dimensione e segno dichiarati. */
    private fun readRaw(buffer: ByteBuffer, size: Int, encoding: String?): Double {
        val signed = encoding?.startsWith("i") == true
        return when (size) {
            1 -> buffer.get().let { if (signed) it.toDouble() else (it.toInt() and 0xFF).toDouble() }
            2 ->
                    buffer.short.let {
                        if (signed) it.toDouble() else (it.toInt() and 0xFFFF).toDouble()
                    }
            4 ->
                    buffer.int.let {
                        if (signed) it.toDouble() else (it.toLong() and 0xFFFFFFFFL).toDouble()
                    }
            else -> throw ReportTruncated("Dimensione di metrica non prevista: $size byte")
        }
    }
}
