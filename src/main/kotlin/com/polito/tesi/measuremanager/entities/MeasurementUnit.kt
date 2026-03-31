package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*

@Entity
class MeasurementUnit {
    // Automatic Id
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    // this is the EUID
    @Column(unique = true)
    var extendedId: Long = 0

    var localId: Int = 0

    @ManyToOne
    lateinit var user: User

    // is a number that it is used to implement a default set of sensors
    var model: Int = 0

    @OneToMany(mappedBy = "measurementUnit", cascade = [CascadeType.ALL], orphanRemoval = true)
    var sensors: MutableList<Sensor> = mutableListOf()

    @ManyToOne
    @JoinColumn(name = "control_unit_id", nullable = true)
    var controlUnit: ControlUnit? = null
}
