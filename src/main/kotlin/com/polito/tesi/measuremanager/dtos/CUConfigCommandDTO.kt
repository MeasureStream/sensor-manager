package com.polito.tesi.measuremanager.dtos

data class CUConfigCommandDTO(
    val deviceId: String,       // Per sapere a chi inviare il comando
    val devEui: String,           // Spesso necessario per il routing hardware
    val pollingInterval: Int    // Il nuovo valore da impostare
)

data class DownlinkRequestDTO(
    val deviceId: String,
    val rawPayload: ByteArray,
    val fPort: Int = 15,
    val priority: String = "NORMAL",
    val confirmed: Boolean = false
) {
    // Override necessario per stampare i byte in modo leggibile nei log
    override fun toString(): String {
        return "DownlinkRequestDTO(deviceId='$deviceId', fPort=$fPort, payload=${rawPayload.joinToString("") { "%02x".format(it) }})"
    }
}
