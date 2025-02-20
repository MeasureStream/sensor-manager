package com.polito.tesi.measuremanager.entities


/*
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PastOrPresent
import java.time.Instant

@Entity
class Measures {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0

    var _value : Double = 0.0
    @NotBlank
    lateinit var measureUnit: String

    //@PastOrPresent risolvere questo problema non si riesce a fare la validation dei DTO
    lateinit var time : Instant

    @ManyToOne
    lateinit var measurementUnit : MeasurementUnit

    @ManyToOne
    lateinit var controlUnit: ControlUnit


}

@Document(collection = "measures")
data class Measures (
    @Id val id: String? = null,
    val measurementUnitNId : Long,
    val controlUnitNId : Long,
    val timestamp: Instant,
    val value: Double,
    val unit: String
)

 */