package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
        name = "measurements",
        indexes =
                [
                        Index(
                                name = "idx_measurements_sensor_time",
                                columnList = "sensor_id, timestamp DESC"
                        ),
                        Index(
                                name = "idx_measurements_type_time",
                                columnList = "measurement_type, timestamp DESC"
                        )]
)
class Measurement(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @Column(nullable = false) var timestamp: OffsetDateTime = OffsetDateTime.now(),
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "sensor_id", nullable = false)
        var sensor: Sensor,
        @Column(name = "measurement_type", length = 50) var measurementType: String? = null,
        @Column(name = "value_primary") var valuePrimary: Double? = null,
        @Column(name = "value_secondary") var valueSecondary: Double? = null
)
