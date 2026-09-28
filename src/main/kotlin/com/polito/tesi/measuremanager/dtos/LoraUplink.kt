package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Un uplink LoRaWAN come arriva dal trasporto: chi l'ha mandato, su quale porta, quando, e i
 * byte esattamente come sono.
 *
 * E' l'unica forma con cui i messaggi entrano in sensor-manager. kafka-stream non interpreta
 * piu' nessun payload: la FPort e' il codice operativo, e sapere cosa significhi un byte
 * richiede i template, la topologia della CU e l'istantanea della configurazione — tre cose
 * che stanno qui e che qui si aggiornano nella stessa transazione.
 *
 * Il campo [fCnt] e' il contatore di frame LoRaWAN: un salto significa uplink persi.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LoraUplink(
        val devEui: Long,
        val deviceId: String,
        val fport: Int,
        /** I byte del payload, in base64, senza nessuna interpretazione. */
        val rawPayload: String,
        val timestamp: String?,
        val fCnt: Int = 0,
)
