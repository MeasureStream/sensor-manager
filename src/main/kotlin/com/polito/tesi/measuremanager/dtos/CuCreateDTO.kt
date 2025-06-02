package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit

data class CuCreateDTO(val networkId:Long, val userId : String)

fun ControlUnit.toCuCreateDTO() = CuCreateDTO(networkId, user.userId)