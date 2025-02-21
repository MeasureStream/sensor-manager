package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import jakarta.validation.constraints.*


@Entity
class ControlUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    @NotNull(message = "networkId is mandatory")
    @Column(unique = true)
    var networkId: Long = 0

    @NotBlank( message= "name is mandatory" )
    lateinit var name :String
    @PositiveOrZero
    @Max(100)
    var remainingBattery : Double = 0.0
    @NegativeOrZero(message = "rssi must be negative")
    var rssi : Double = 0.0



    @ManyToOne
    var node :  Node? = null

}