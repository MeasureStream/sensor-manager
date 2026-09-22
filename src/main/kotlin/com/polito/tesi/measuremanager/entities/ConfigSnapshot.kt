package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * La configurazione di una CU al momento in cui CFG_VER e' stato incrementato.
 *
 * E' l'«epoca» del protocollo: un report che arriva con un CFG_VER vecchio non e'
 * indecodificabile, e' semplicemente scritto secondo un'altra mappa degli slot. Conservare
 * quella mappa trasforma un report scartato in un report letto.
 */
@Entity
@Table(
        name = "config_snapshot",
        uniqueConstraints =
                [
                        UniqueConstraint(
                                name = "config_snapshot_unique",
                                columnNames = ["control_unit_id", "cfg_version"]
                        )]
)
class ConfigSnapshot(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "control_unit_id", nullable = false)
        var controlUnit: ControlUnit,
        @Column(name = "cfg_version", nullable = false) var cfgVersion: Long = 0,
        @Column(name = "taken_at", nullable = false) var takenAt: OffsetDateTime = OffsetDateTime.now(),
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(nullable = false, columnDefinition = "jsonb")
        var content: Map<String, Any?> = emptyMap(),
) {
    /** Gli slot dell'istantanea, nell'ordine globale in cui viaggiano sul filo. */
    @Suppress("UNCHECKED_CAST")
    val slots: List<Map<String, Any?>>
        get() = (content["slots"] as? List<Map<String, Any?>>) ?: emptyList()
}
