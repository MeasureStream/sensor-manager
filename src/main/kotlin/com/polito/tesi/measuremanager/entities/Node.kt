package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.data.geo.Point

@Entity
class Node {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    @NotBlank
    lateinit var name: String


    var standard :  Boolean = false

    @OneToMany(mappedBy = "node", orphanRemoval = true)
    lateinit var controlUnits :  MutableSet<ControlUnit>

    @OneToMany(mappedBy = "node", orphanRemoval = true)
    lateinit var measurementUnits :  MutableSet<MeasurementUnit>

    lateinit var location : Point

    //@Column(nullable = false)
   // lateinit var ownerId: String  // <-- ID utente Keycloak

    @ManyToOne
    lateinit var user: User

}