package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.MetricSample
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MetricSampleRepository : JpaRepository<MetricSample, Long> {

    /** Le ultime misure di un sensore, metrica per metrica. */
    fun findTop100BySensor_IdOrderByTimestampDesc(sensorId: Long): List<MetricSample>

    fun findBySensor_IdAndMetricAndTimestampBetweenOrderByTimestamp(
            sensorId: Long,
            metric: String,
            from: OffsetDateTime,
            to: OffsetDateTime,
    ): List<MetricSample>
}
