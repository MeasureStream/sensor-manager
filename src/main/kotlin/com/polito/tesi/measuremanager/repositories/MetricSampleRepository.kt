package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.MetricSample
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MetricSampleRepository : JpaRepository<MetricSample, Long> {

    /** Le ultime misure di un sensore, metrica per metrica. */
    fun findTop100BySensor_IdOrderByTimestampDesc(sensorId: Long): List<MetricSample>

    /** L'ultimo campione di una delle metriche indicate: lo stato di qualita' attuale. */
    fun findTopBySensor_IdAndMetricInOrderByTimestampDesc(sensorId: Long, metrics: Collection<String>): MetricSample?

    /** Tutte le metriche di un sensore in un intervallo: l'esportazione CSV. */
    fun findBySensor_IdAndTimestampBetweenOrderByTimestampAscMetricAsc(
            sensorId: Long,
            from: OffsetDateTime,
            to: OffsetDateTime,
    ): List<MetricSample>

    fun findBySensor_IdAndMetricAndTimestampBetweenOrderByTimestamp(
            sensorId: Long,
            metric: String,
            from: OffsetDateTime,
            to: OffsetDateTime,
    ): List<MetricSample>
}
