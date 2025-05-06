package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.LocalDate

@Entity
class DCC {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    lateinit var expiration: LocalDate

    lateinit var filename: String

    @Column(columnDefinition = "bytea")
    lateinit var pdf: ByteArray

    @OneToOne
    @JoinColumn(name = "mu_id", unique = true)
    var mu: MeasurementUnit? = null

}

