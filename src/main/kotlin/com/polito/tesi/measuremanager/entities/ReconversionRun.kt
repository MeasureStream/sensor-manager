package com.polito.tesi.measuremanager.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * GAP: i campioni fra l'as-found e l'attivazione, riconvertiti con la funzione nuova.
 * DRIFT: i campioni del periodo precedente, con la funzione interpolata nel tempo fra l'as-left
 * precedente e l'as-found attuale.
 */
enum class ReconversionMode { GAP, DRIFT }

enum class ReconversionState { PENDING, RUNNING, DONE, FAILED }

/** Una riconversione (PIANO_METROLOGIA 5.1, specifica § 4.4): `value` non si tocca mai. */
@Entity
@Table(name = "reconversion_run")
class ReconversionRun(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @Column(name = "mu_extended_id", nullable = false) var muExtendedId: Long,
        @Column(name = "sensor_index", nullable = false) var sensorIndex: Int,
        /** La taratura che porta la conoscenza nuova: la sua funzione, o il suo as-found. */
        @Column(name = "calibration_id", nullable = false) var calibrationId: Long,
        @Column(name = "certificate_id") var certificateId: Long? = null,
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) var mode: ReconversionMode,
        @Column(name = "from_ts", nullable = false) var fromTs: OffsetDateTime,
        @Column(name = "to_ts", nullable = false) var toTs: OffsetDateTime,
        @Column(name = "requested_by", nullable = false, length = 64) var requestedBy: String,
        @Column(columnDefinition = "text") var reason: String? = null,
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var state: ReconversionState = ReconversionState.PENDING,
        @Column(name = "cursor_id", nullable = false) var cursorId: Long = 0,
        @Column(nullable = false) var samples: Int = 0,
        @Column(nullable = false) var skipped: Int = 0,
        @Column(columnDefinition = "text") var error: String? = null,
        @Column(name = "created_at", nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now(),
        @Column(name = "started_at") var startedAt: OffsetDateTime? = null,
        @Column(name = "finished_at") var finishedAt: OffsetDateTime? = null,
)
