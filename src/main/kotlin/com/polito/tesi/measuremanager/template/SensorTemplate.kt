package com.polito.tesi.measuremanager.template

/**
 * Una metrica che il modello sa produrre, nell'ordine canonico del protocollo (capitolo 4.6).
 *
 * [bytes] ed [encoding] dicono come leggere il campo nel report, [transform] come convertirlo.
 * Sono i campi che permettono di decodificare senza sapere nulla del sensore.
 */
data class MetricSpec(
        val id: Int = 0,
        val name: String = "",
        val bytes: Int = 2,
        /** u16, i16, u32, i32. Assente: si assume senza segno della dimensione dichiarata. */
        val encoding: String? = null,
        /** elec (lettura grezza) o phys (gia' tarata a bordo). */
        val domain: String? = null,
        /** calibration, variance, integral, none. */
        val transform: String? = null,
)

data class SensorTemplate(
        val modelName: String,
        val type: String,
        val unit: String? = null,
        /**
         * Cosa trasmette il dispositivo: `uncalibrated` la lettura grezza (il server applica
         * la formula), `calibrated` la stima gia' tarata. Default del modello; la singola
         * metrica puo' sovrascriverlo con `domain`.
         */
        val outputFormat: String? = null,
        /** Le metriche che il modello sa produrre, in ordine canonico. */
        val supportedMetrics: List<MetricSpec>? = null,
        val ranges: Map<String, Map<String, Any>>? = null,
        val conversion: Map<String, Any>? = null,
        val properties: Map<String, Any>? = null,
        val calibration: Map<String, Any>? = null,
        val metrology: Map<String, Any>? = null,
)
