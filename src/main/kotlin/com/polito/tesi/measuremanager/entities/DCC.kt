package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.LocalDate

@Entity
class DCC {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    lateinit var scadenza: LocalDate

    @Lob
    @Basic(fetch = FetchType.LAZY)
    lateinit var pdf: ByteArray
/*
    @OneToOne
    @JoinColumn(name = "node_id", unique = true)
    var node: Node? = null
*/
}

