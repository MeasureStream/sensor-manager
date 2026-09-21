package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
        name = "signal_qualities",
        indexes =
                [
                        Index(
                                name = "idx_signal_cu_time",
                                columnList = "control_unit_id, timestamp DESC"
                        ),
                        Index(name = "idx_signal_time", columnList = "timestamp DESC")]
)
class SignalQuality(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @Column(nullable = false) var timestamp: OffsetDateTime = OffsetDateTime.now(),
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "control_unit_id", nullable = false)
        var controlUnit: ControlUnit,
        @Column(name = "rssi") var rssi: Double? = null,
        @Column(name = "snr") var snr: Double? = null,
        @Column(name = "data_rate") var dataRate: Int? = null,
        @Column(name = "airtime") var airtime: Double? = null
)
