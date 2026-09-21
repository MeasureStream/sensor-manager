package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/** Il tipo di documento contenuto nel registro. */
enum class TemplateKind {
    SENSOR,
    REFERENCE,
    MU,
    CU,
    PROTOCOL,
    ;

    companion object {
        fun fromString(value: String): TemplateKind? =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Stato di una versione. DEV esiste solo dentro il MAJOR 0 e si puo' sovrascrivere;
 * PUBLISHED e REVOKED non si modificano piu' e nessuna versione si cancella, perche' una
 * misura storica va riletta con il template con cui e' stata decodificata.
 */
enum class TemplateStatus {
    DEV,
    PUBLISHED,
    REVOKED,
}

@Entity
@Table(
        name = "template",
        uniqueConstraints =
                [
                        UniqueConstraint(
                                name = "template_version_unique",
                                columnNames = ["kind", "key_id", "major", "minor", "patch"]
                        )]
)
class TemplateRecord(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 16)
        var kind: TemplateKind,
        @Column(name = "key_id", nullable = false) var keyId: Int,
        @Column(nullable = false) var major: Int,
        @Column(nullable = false) var minor: Int,
        @Column(nullable = false) var patch: Int,
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 16)
        var status: TemplateStatus,
        @Column(name = "model_name") var modelName: String? = null,
        @Column(name = "schema_version", length = 32) var schemaVersion: String? = null,
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(nullable = false, columnDefinition = "jsonb")
        var content: Map<String, Any?>,
        @Column(name = "content_hash", nullable = false, length = 64) var contentHash: String,
        @Column(nullable = false, length = 32) var source: String = "api",
        @Column(name = "published_by") var publishedBy: String? = null,
        @Column(name = "published_at", nullable = false)
        var publishedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    val version: String
        get() = "$major.$minor.$patch"
}
