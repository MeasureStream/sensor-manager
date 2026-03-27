package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.MeasurementUnit
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDate

// This DTO is used to communicate between frontend and backend
data class MeasurementUnitDTO(
    val id :Long,
    @NotEmpty
    val networkId: Long,
    @NotBlank
    val model:Int,

    val nodeId : Long?,
    val sensors: List<SensorDTO> = emptyList()
)

fun MeasurementUnit.toDTO() = MeasurementUnitDTO(id,networkId,model,node?.id)