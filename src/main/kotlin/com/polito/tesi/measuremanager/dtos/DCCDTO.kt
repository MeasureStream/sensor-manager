package com.polito.tesi.measuremanager.dtos
import com.polito.tesi.measuremanager.entities.DCC
import java.time.LocalDate

data class DCCDTO(
    val id: Long,
    val expiration: LocalDate,
    val muId: Long? = null,
    val filename: String,
)

fun DCC.toDTO() = DCCDTO(id,expiration, mu?.id, filename)