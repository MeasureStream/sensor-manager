package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CuMeasuresUpdate(
        val devEui: Long = 0L,
        val deviceId: String = "",
        val configVersion: Int = 0,
        val rawPayload: String = ""
)
