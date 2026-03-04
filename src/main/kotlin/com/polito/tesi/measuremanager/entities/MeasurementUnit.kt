package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import com.polito.tesi.measuremanager.entities.Sensor

@Entity
class MeasurementUnit {

    // Automatic Id
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    // this is the EUID
    @Column(unique = true)
    var networkId: Long = 0


    @ManyToOne
    var node : Node? = null


    @ManyToOne
    lateinit var user: User

    // is a number that it is used to implement a default set of sensors
    var model: Int = 0

    @OneToMany(mappedBy = "measurementUnit", cascade = [CascadeType.ALL], orphanRemoval = true)
    var sensors: MutableList<Sensor> = mutableListOf()
}
