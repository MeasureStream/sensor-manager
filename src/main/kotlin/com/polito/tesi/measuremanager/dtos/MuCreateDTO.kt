package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.MeasurementUnit

data class MuCreateDTO(
    val networkId: Long,
    val userId: String,
    val model: Int,
)

fun MeasurementUnit.toMUCreateDTO() = MuCreateDTO(extendedId, user.userId, model)
