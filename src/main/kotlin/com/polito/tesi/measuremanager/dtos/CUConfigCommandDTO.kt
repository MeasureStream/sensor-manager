package com.polito.tesi.measuremanager.dtos

data class CUConfigCommandDTO(
    val deviceId: String,       // Per sapere a chi inviare il comando
    val devEui: String,           // Spesso necessario per il routing hardware
    val pollingInterval: Int    // Il nuovo valore da impostare
)
