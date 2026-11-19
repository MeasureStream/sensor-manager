package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.OutboxEvent
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    /** I prossimi da spedire, nell'ordine in cui sono nati. */
    fun findTop200BySentAtIsNullOrderByIdAsc(): List<OutboxEvent>

    fun countBySentAtIsNull(): Long

    /** Quelli consegnati da piu' di una settimana non servono piu' a nessuno. */
    @Modifying
    @Query("delete from OutboxEvent e where e.sentAt is not null and e.sentAt < :before")
    fun deleteSentBefore(before: OffsetDateTime): Int
}
