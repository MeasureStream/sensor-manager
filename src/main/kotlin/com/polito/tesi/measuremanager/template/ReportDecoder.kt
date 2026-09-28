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
        /** Tutti i byte dello slot a 0xFF: il sensore non ha risposto alla CU. */
        val notResponding: Boolean = false,
)

/** Il report non si puo' leggere: byte mancanti o metrica non descritta. */
class ReportTruncated(message: String) : Exception(message)

/**
 * Legge le metriche di uno slot dal report, usando il template del sensore.
 *
 * Quanti byte occupa una metrica, se ha segno e come si converte non stanno piu' nel codice:
 * stanno in `supportedMetrics` del template, e l'ordine in cui viaggiano nel dizionario di
 * protocollo. Un sensore nuovo si aggiunge pubblicando un documento, e una metrica in piu'
 * non richiede di toccare il decoder.
 */
@Service
class ReportDecoder(
        private val conversion: SensorConversion,
        private val protocol: ProtocolService,
) {
    private val logger = LoggerFactory.getLogger(ReportDecoder::class.java)

    /** Sentinella del protocollo: media e varianza entrambe a questo valore. */
    private val noNewDataSentinel = 0xFFFF

    /**
     * Le metriche attese per uno slot, nell'ordine in cui viaggiano.
     *
     * Due filtri in fila: la `Stat bitmap` dice quali metriche quel sensore calcola, il byte
     * CONTENT quali **classi** viaggiano in questo report. La seconda e' la ragione per cui
     * CONTENT esiste: mandare le EXTENDED una volta ogni n cicli riduce i pacchetti nella
     * stessa proporzione, senza cambiare formato ne' firmware. Un report senza CONTENT (la
     * vecchia 0x21) non filtra per classe: porta tutto quello che il sensore ha configurato.
     */
    fun expectedMetrics(configurationMeasure: String?, content: Int? = null): List<String> {
        // Il valore corrente non appartiene all'ordine canonico: e' un nome locale ereditato.
        if (StatBitmap.isPunctual(configurationMeasure)) return listOf(Metric.PUNCTUAL)

        val table = protocol.metrics()
        if (table.isEmpty()) {
            // Senza dizionario resta la traduzione minima: meglio leggere il caso di gran
            // lunga piu' comune che non leggere niente.
            logger.debug("Dizionario di protocollo assente: ordine canonico non risolto")
            return legacyMetrics(configurationMeasure)
        }

        val bitmap = StatBitmap.of(configurationMeasure)
        val classes = protocol.classesIn(content)

        return table.filter { (bitmap shr it.bit) and 1 == 1 }
                .filter { classes == null || it.metricClass in classes }
                .map { it.name }
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
        val sizes = LinkedHashMap<String, Int>()

        for (metric in metrics) {
            val spec = specOf(template, metric)
            val size = spec?.bytes ?: canonicalBytes(metric) ?: 2
            if (buffer.remaining() < size) {
                throw ReportTruncated(
                        "$slotLabel, metrica $metric: servivano $size byte, ne restavano ${buffer.remaining()}"
                )
            }
            raws[metric] = readRaw(buffer, size, spec?.encoding)
            sizes[metric] = size
        }

        // Sentinelle, nell'ordine di precedenza della documentazione (§ 4.9).
        //
        // FF ovunque nella porzione del sensore: la CU non e' riuscita a interrogarlo. E' un
        // superset del caso sotto e va verificato per primo, altrimenti un sensore muto
        // sembrerebbe soltanto «senza dati nuovi».
        if (raws.isNotEmpty() && raws.all { (metric, raw) -> raw == maxUnsigned(sizes[metric] ?: 2) }) {
            logger.debug("{}: nessuna risposta dal sensore", slotLabel)
            return raws.map { (metric, raw) -> DecodedMetric(metric, raw, null, false, notResponding = true) }
        }

        // Media e varianza entrambe al massimo: nessuna misura nuova dall'ultima lettura, di
        // solito perche' il periodo di trasmissione e' piu' corto di quello di campionamento.
        // 0x0000 invece e' un valore legittimo.
        val mean = raws[Metric.MEAN]
        val variance = raws[Metric.VARIANCE]
        if (mean != null &&
                        variance != null &&
                        mean.toInt() == noNewDataSentinel &&
                        variance.toInt() == noNewDataSentinel
        ) {
            logger.debug("{}: nessun dato nuovo nell'intervallo", slotLabel)
            return raws.map { (metric, raw) -> DecodedMetric(metric, raw, null, false, noNewData = true) }
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

    /** La traduzione minima di `configurationMeasure`, per quando il dizionario manca. */
    private fun legacyMetrics(configurationMeasure: String?): List<String> =
            when (configurationMeasure?.lowercase()) {
                "max-min" -> listOf(Metric.MAX, Metric.MIN)
                "integral" -> listOf(Metric.INTEGRAL)
                else -> listOf(Metric.MEAN, Metric.VARIANCE)
            }

    /**
     * La dimensione dichiarata dall'ordine canonico, per le metriche che il template non
     * descrive. Serve soprattutto all'integrale, che occupa quattro byte: leggerne due
     * sposterebbe di due byte tutto il resto del report.
     */
    private fun canonicalBytes(metric: String): Int? =
            protocol.metrics().firstOrNull { it.name == metric }?.bytes

    private fun maxUnsigned(size: Int): Double =
            when (size) {
                1 -> 255.0
                2 -> 65535.0
                4 -> 4294967295.0
                else -> Double.NaN
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
