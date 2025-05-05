package com.polito.tesi.measuremanager.dtos
import com.polito.tesi.measuremanager.entities.DCC
import java.time.LocalDate

data class DCCDTO(
    val id: Long,
    val scadenza: LocalDate,
    val nodeId: Long? = null
)

fun DCC.toDTO() = DCCDTO(id,scadenza, nodeId = null)