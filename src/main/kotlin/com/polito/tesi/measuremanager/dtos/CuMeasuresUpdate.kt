package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CuMeasuresUpdate(
        val devEui: Long,
        val deviceId: String,
        val configVersion: Int,
        val rawPayload: String,
        val timestamp: String?
)
