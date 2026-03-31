package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.geo.Point

@Entity
class ControlUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    var model: Int = 0

    @Column(unique = true, nullable = false)
    var devEui: Long = 0

    @NotBlank(message = "name is mandatory")
    lateinit var name: String

    @PositiveOrZero
    @Max(100)
    var remainingBattery: Double = 0.0

    @NegativeOrZero(message = "rssi must be negative")
    var rssi: Double = 0.0

    var location: Point? = null

    @ManyToOne
    var user: User? = null

    var status: Int = 0

    var dataRate: Int = 0
    var usedDC : Int = 0
    var hasGPS : Boolean = false
    var MaxMU : Int = 0

    @OneToMany(mappedBy = "controlUnit")
    var measurementUnits: MutableList<MeasurementUnit> = mutableListOf()


    /**
     * Corrisponde a Setting1 (Byte 1).
     * Gestito come Int per facilitare operazioni bitwise.
     */
    var setting1: Int = 0

    /**
     * Corrisponde a P_TX (Byte 2).
     * Potenza di trasmissione.
     */
    var transmissionPower: Int = 0

    /**
     * Corrisponde a Delta T_Polling (Byte 3).
     * Periodo tra due messaggi di stato.
     */
    var pollingInterval: Int = 0

    var semanticLocation : String = ""
    var bandwidth: Int = 0
    var spreadingFactor: Int = 0
    var codingRate: String = ""
    var frequency: Int = 0
}
