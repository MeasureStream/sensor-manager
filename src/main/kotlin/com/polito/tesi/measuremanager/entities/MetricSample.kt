package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * I nomi dell'ordine canonico delle metriche (capitolo 4.6 della documentazione v1.2).
 *
 * Non sono un enum del database: restano stringhe, cosi' una metrica nuova non obbliga a una
 * migrazione. Questa costante serve a non scriverle a mano nel codice.
 */
object Metric {
    const val MEAN = "mean"
    const val VARIANCE = "variance"
    const val MAX = "max"
    const val MIN = "min"
    const val INTEGRAL = "integral"
    const val TOT_H = "tot_h"
    const val TOT_L = "tot_l"
    const val MEDIAN = "median"
    const val P_HIGH = "p_high"
    const val P_LOW = "p_low"
    const val ZCR = "zcr"

    /** Nome locale, ereditato dalle righe storiche: non appartiene all'ordine canonico. */
    const val PUNCTUAL = "punctual"
}

/**
 * Il valore di una metrica per un sensore in un istante.
 *
 * Una riga per metrica, non due colonne il cui significato dipende da una stringa: cosi' le
 * undici metriche del protocollo entrano senza accoppiamenti inventati, e la dodicesima non
 * richiedera' nessuna migrazione.
 */
@Entity
@Table(
        name = "metric_sample",
        indexes =
                [
                        Index(
                                name = "idx_metric_sample_sensor_metric_time",
                                columnList = "sensor_id, metric, timestamp DESC"
                        )]
)
class MetricSample(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "sensor_id", nullable = false)
        var sensor: Sensor,
        @Column(nullable = false) var timestamp: OffsetDateTime = OffsetDateTime.now(),
        @Column(nullable = false, length = 16) var metric: String,
        /** Valore mostrato: fisico se [converted], altrimenti la lettura elettrica. */
        @Column var value: Double? = null,
        /** Il grezzo com'e' arrivato sul filo, per riconvertire senza rileggere il frame. */
        @Column(name = "raw_value") var rawValue: Double? = null,
        /** Null sulle righe storiche, dove non e' ricostruibile con certezza. */
        @Column var converted: Boolean? = null,
        /** Versione esatta del template usata per decodificare. */
        @Column(name = "template_version", length = 16) var templateVersion: String? = null,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "uplink_frame_id")
        var uplinkFrame: UplinkFrame? = null,
)
