package com.polito.tesi.measuremanager.kafka

/**
 * Rappresenta un comando già pronto per essere spedito via LoRaWAN
 */
data class EncodedPayload(
    val bytes: ByteArray,
    val fPort: Int
)
