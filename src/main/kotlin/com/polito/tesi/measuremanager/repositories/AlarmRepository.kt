package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.SensorAlarm
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SensorAlarmRepository : JpaRepository<SensorAlarm, Long> {

    fun findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId: Long): List<SensorAlarm>

    fun countByControlUnit_IdAndClearedFalseAndAcknowledgedAtIsNull(controlUnitId: Long): Long

    @Modifying
    @Query(
            """
            update SensorAlarm a set a.acknowledgedAt = :now
            where a.controlUnit.id = :controlUnitId and a.acknowledgedAt is null
            """
    )
    fun acknowledgeAll(controlUnitId: Long, now: OffsetDateTime): Int
}

@Repository
interface DeviceEventRepository : JpaRepository<DeviceEvent, Long> {

    fun findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId: Long): List<DeviceEvent>

    @Modifying
    @Query(
            """
            update DeviceEvent e set e.acknowledgedAt = :now
            where e.controlUnit.id = :controlUnitId and e.acknowledgedAt is null
            """
    )
    fun acknowledgeAll(controlUnitId: Long, now: OffsetDateTime): Int
}
