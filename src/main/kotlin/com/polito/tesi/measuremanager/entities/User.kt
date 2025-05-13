package com.polito.tesi.measuremanager.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "app_user")  // nome alternativo, non riservato
class User {
    @Id
    lateinit var userId : String

    lateinit var name:String

    lateinit var surname:String

    lateinit var email:String

    lateinit var role:String

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    lateinit var nodes : MutableSet<Node>

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    lateinit var mus: MutableSet<MeasurementUnit>

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    lateinit var cus: MutableSet<ControlUnit>
}