package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

@Entity
class MeasurementUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    @NotBlank
    lateinit var type: String
    @NotBlank
    lateinit var measuresUnit : String

    @Column(unique = true)
    var networkId: Long = 0

    //@Column(unique = true)
    //@PositiveOrZero
    //var idDcc : Long = 0

    /*@OneToMany(mappedBy = "measurementUnit")
    lateinit var measures : MutableList<Measures>*/

    /*@OneToOne(mappedBy = "measurementUnit")
    var controlUnit : ControlUnit? = null*/
    @ManyToOne
    var node :  Node? = null


    @OneToOne(mappedBy = "mu", cascade = [CascadeType.ALL], orphanRemoval = true)
    var dcc: DCC? = null

    @ManyToOne
    lateinit var user: User
}