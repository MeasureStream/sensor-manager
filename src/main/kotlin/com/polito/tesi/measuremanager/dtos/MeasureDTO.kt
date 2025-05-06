package com.polito.tesi.measuremanager.dtos


import jakarta.validation.constraints.NotBlank

import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant


data class MeasureDTO(

    val id: Long ,
    val value: Double,

    @NotBlank
    val measureUnit: String ,
    @PastOrPresent
    val time: Instant,

    @PositiveOrZero
    val measurementUnitNId: Long,

    @PositiveOrZero
    val controlUnitNId: Long


)
/*
fun Measures.toDTO() = MeasureDTO(id,_value,measureUnit,time, measurementUnit.networkId, controlUnit.networkId)
*/
