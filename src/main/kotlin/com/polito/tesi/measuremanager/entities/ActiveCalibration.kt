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
 * La funzione di taratura di un esemplare, come l'ha attivata metrology-service.
 *
 * L'esemplare si riconosce dalla chiave naturale (EUID della MU, indice del sensore), non
 * dalla riga `sensor`: e' lo strumento fisico che e' stato tarato, e resta lo stesso se la
 * MU cambia CU o se il sensore viene ricreato dalla topologia.
 *
 * Questa tabella e' una proiezione del topic compattato `calibration-activated`: la scrive
 * solo il suo consumer, e si ricostruisce rileggendo il topic.
 */
@Entity
@Table(name = "active_calibration")
class ActiveCalibration(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @Column(name = "calibrated_item_id") var calibratedItemId: Long? = null,
        @Column(name = "mu_extended_id", nullable = false) var muExtendedId: Long,
        @Column(name = "sensor_index", nullable = false) var sensorIndex: Int,
        @Column(name = "certificate_id", nullable = false) var certificateId: Long,
        @Column(name = "certificate_number", length = 64) var certificateNumber: String? = null,
        @Column(nullable = false) var revision: Int = 1,
        /** Stessa forma del blocco `calibration` di un template. */
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb", nullable = false)
        var function: Map<String, Any?>,
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        var covariance: List<List<Double>>? = null,
        @Column(name = "calibrated_min") var calibratedMin: Double? = null,
        @Column(name = "calibrated_max") var calibratedMax: Double? = null,
        @Column(name = "valid_from", nullable = false) var validFrom: OffsetDateTime,
        @Column(name = "valid_until") var validUntil: OffsetDateTime? = null,
        @Column(name = "revoked_at") var revokedAt: OffsetDateTime? = null,
        @Column(name = "block_a_crc_expected", length = 8) var blockACrcExpected: String? = null,
        @Column(length = 80) var fingerprint: String? = null,
        @Column(name = "received_at", nullable = false) var receivedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    /** In uso all'istante [at]: iniziata e non revocata prima di allora. La scadenza no. */
    fun appliesAt(at: OffsetDateTime): Boolean =
            !validFrom.isAfter(at) && (revokedAt == null || at.isBefore(revokedAt))

    /** Scaduta all'istante [at]: la funzione resta la migliore che si conosca, ma lo si dice. */
    fun expiredAt(at: OffsetDateTime): Boolean = validUntil != null && !at.isBefore(validUntil)
}
