package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Busta di un messaggio di allarme (0xA0) o di evento (0xA2).
 *
 * kafka-stream non interpreta questi payload: i testi delle condizioni e i codici dei
 * costruttori vivono nei template, e i template stanno nel registro di sensor-manager.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CuAlarmUpdate(
        val devEui: Long,
        val deviceId: String,
        val fport: Int,
        val rawPayload: String,
        val timestamp: String?,
)
