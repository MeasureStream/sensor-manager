package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/** Esito della lettura di un frame. */
enum class FrameStatus {
    /** Letto e misure salvate. */
    DECODED,

    /** La CU dichiarava un CFG_VER diverso da quello atteso: la mappa degli slot non e' nota. */
    DISCARDED_CFG_MISMATCH,

    /** Byte mancanti, payload corrotto, sensori non corrispondenti. */
    DISCARDED_DECODE_ERROR,
}

/**
 * Un frame ricevuto, con il payload grezzo e il risultato della decodifica.
 *
 * Il grezzo si conserva sempre, anche quando la lettura riesce: e' l'unica prova di cosa sia
 * davvero arrivato, e permette di rileggere un report in seguito — per esempio quando si
 * scopre che una formula di conversione era sbagliata.
 */
@Entity
@Table(name = "uplink_frame")
class UplinkFrame(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "control_unit_id")
        var controlUnit: ControlUnit? = null,
        @Column(name = "dev_eui", nullable = false) var devEui: Long = 0,
        @Column(name = "received_at", nullable = false)
        var receivedAt: OffsetDateTime = OffsetDateTime.now(),
        @Column var fport: Int? = null,
        @Column(name = "cfg_version") var cfgVersion: Int? = null,
        @Column(name = "expected_cfg_version") var expectedCfgVersion: Int? = null,
        @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
        var rawPayload: String = "",
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        var decoded: Map<String, Any?>? = null,
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 32)
        var status: FrameStatus = FrameStatus.DECODED,
        @Column(name = "failure_reason", columnDefinition = "text")
        var failureReason: String? = null,
        @Column(name = "acknowledged_at") var acknowledgedAt: OffsetDateTime? = null,
)
