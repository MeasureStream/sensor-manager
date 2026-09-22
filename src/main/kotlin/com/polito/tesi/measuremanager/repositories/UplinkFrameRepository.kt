package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.FrameStatus
import com.polito.tesi.measuremanager.entities.UplinkFrame
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UplinkFrameRepository : JpaRepository<UplinkFrame, Long> {

    /** Gli ultimi frame di una CU, per la scheda diagnostica. */
    fun findTop50ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId: Long): List<UplinkFrame>

    /** Quelli scartati e non ancora presi in carico: e' il numero che l'utente azzera. */
    fun countByControlUnit_IdAndStatusNotAndAcknowledgedAtIsNull(
            controlUnitId: Long,
            status: FrameStatus,
    ): Long

    @Modifying
    @Query(
            """
            update UplinkFrame f set f.acknowledgedAt = :now
            where f.controlUnit.id = :controlUnitId and f.acknowledgedAt is null
            """
    )
    fun acknowledgeAll(controlUnitId: Long, now: OffsetDateTime): Int

    /** Pulizia per anzianita': la ritenzione e' di un anno. */
    @Modifying
    @Query("delete from UplinkFrame f where f.receivedAt < :before")
    fun deleteReceivedBefore(before: OffsetDateTime): Int
}
