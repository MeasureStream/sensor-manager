package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.SignalQuality
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SignalQualityRepository : JpaRepository<SignalQuality, Long> {

    fun findByControlUnitAndTimestampBetweenOrderByTimestampAsc(
            controlUnit: ControlUnit,
            start: OffsetDateTime,
            end: OffsetDateTime
    ): List<SignalQuality>

    fun findByControlUnitIdAndTimestampBetweenOrderByTimestampAsc(
            controlUnitId: Long,
            start: OffsetDateTime,
            end: OffsetDateTime
    ): List<SignalQuality>

    fun findFirstByControlUnitIdOrderByTimestampDesc(controlUnitId: Long): SignalQuality?

    @Query(
            """
        SELECT sq FROM SignalQuality sq 
        WHERE sq.controlUnit.id = :controlUnitId 
          AND sq.timestamp >= :from
        ORDER BY sq.timestamp ASC
        """
    )
    fun findRecentSignalHistory(
            @Param("controlUnitId") controlUnitId: Long,
            @Param("from") from: OffsetDateTime
    ): List<SignalQuality>
}
