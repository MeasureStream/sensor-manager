package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.MeasurementUnit
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero

data class MeasurementUnitDTO(
    val id :Long,
    @NotEmpty
    val networkId: Long ,
    @NotBlank
    val type:String,
    @NotBlank
    val measuresUnit:String,
    @PositiveOrZero
    val idDcc: Long?,
    //val controlUnitNId: Long?,

    val nodeId : Long?,

    val dccFileNme : String? = null,
)

fun MeasurementUnit.toDTO() = MeasurementUnitDTO(id,networkId,type,measuresUnit, dcc?.id , node?.id, dcc?.filename)