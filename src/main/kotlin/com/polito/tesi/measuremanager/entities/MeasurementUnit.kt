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
    var user: User? = null

    // is a number that it is used to implement a default set of sensors
    var model: Int = 0

    @OneToMany(mappedBy = "measurementUnit", cascade = [CascadeType.ALL], orphanRemoval = true)
    var sensors: MutableList<Sensor> = mutableListOf()

    @ManyToOne
    @JoinColumn( nullable = true)
    var controlUnit: ControlUnit? = null

    /**
     * MAJOR del modello dichiarato dalla MU nella notifica 0x11. E' la versione con cui vanno
     * letti i suoi slot: prima si prendeva il MAJOR piu' alto pubblicato, che e' giusto solo
     * finche' esiste una versione sola. Null quando arriva la vecchia 0x10, che non lo porta.
     */
    var modelMajor: Int? = null

    /** Parola di stato dal comando 0x12: bit 0-6 dalla MU sul bus UART, bit 7-9 dalla CU. */
    var statusWord: Int? = null

    var statusAt: java.time.OffsetDateTime? = null
}
