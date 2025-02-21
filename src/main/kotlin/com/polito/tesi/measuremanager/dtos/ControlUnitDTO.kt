package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.data.geo.Point


data class ControlUnitDTO(
    val id:Long,
    @NotNull
    val networkId: Long,
    @NotBlank(message = "Name is mandatory")
    val name:String,
    @NotNull
    @PositiveOrZero
    @Max( value = 100, message = "the remainingBattery must be under 100" )
    val remainingBattery: Double,
    @NegativeOrZero(message = "rssi must be negative")
    val rssi : Double,

    //val measurementUnitNId: Long?

    val nodeId: Long?
)

fun ControlUnit.toDTO() = ControlUnitDTO(id,networkId,name,remainingBattery,rssi, node?.id)