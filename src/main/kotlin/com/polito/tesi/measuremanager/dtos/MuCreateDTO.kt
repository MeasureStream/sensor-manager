package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.MeasurementUnit

data class MuCreateDTO(val networkId: Long, val userid:String)

fun MeasurementUnit.toMUCreateDTO()  = MuCreateDTO(networkId,user.userId)