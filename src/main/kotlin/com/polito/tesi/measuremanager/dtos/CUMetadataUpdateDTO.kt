package com.polito.tesi.measuremanager.dtos

import jakarta.validation.constraints.Size

data class CUMetadataUpdateDTO(
    val id: Long,
    
    @field:Size(min = 1, message = "Il nome non può essere vuoto")
    val name: String?,
    
    val semanticLocation: String?
)
