package com.polito.tesi.measuremanager.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Un evento da consegnare a Kafka, scritto nella transazione del dato che lo genera.
 *
 * Se il campione esiste, esiste anche l'evento: e' il pubblicatore a spedirlo quando Kafka
 * risponde. Niente si perde in silenzio se il broker e' giu' al momento del report.
 */
@Entity
@Table(name = "outbox_event")
class OutboxEvent(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @Column(nullable = false, length = 64) var topic: String,
        @Column(name = "event_key", length = 64) var eventKey: String? = null,
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb", nullable = false)
        var payload: Map<String, Any?>,
        @Column(name = "created_at", nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now(),
        @Column(name = "sent_at") var sentAt: OffsetDateTime? = null,
        @Column(nullable = false) var attempts: Int = 0,
        @Column(name = "last_error", length = 500) var lastError: String? = null,
)
