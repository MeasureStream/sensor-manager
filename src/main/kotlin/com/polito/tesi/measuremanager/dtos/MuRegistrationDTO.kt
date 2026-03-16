package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonProperty

data class MuRegistrationDTO(
    @JsonProperty("CUID") val cuId: String,
    @JsonProperty("MUID") val muId: String,
    @JsonProperty("MODELMU") val modelMu: String,
)
