package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CUConfigCommandDTO
import com.polito.tesi.measuremanager.dtos.DownlinkRequestDTO
import com.polito.tesi.measuremanager.dtos.EventCU
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaCuProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {
    fun sendCuCreate(event: EventCU) {
        kafkaTemplate.send("cu-creation", event)
        println("Sent message to Kafka topic: cu-creation : $event")
    }

    fun sendPollingUpdate(deviceId: String, event: CUConfigCommandDTO) {
        // 1. Costruiamo il payload binario: | 0x0A | 0x00 | Polling |
        val payload = byteArrayOf(
            0x0A.toByte(),
            0x00.toByte(),
            (event.pollingInterval and 0xFF).toByte()
        )

        // 2. Creiamo il DTO "pulito" per il Sink MQTT
        val downlinkRequest = DownlinkRequestDTO(
            deviceId = deviceId,
            rawPayload = payload,
            fPort = 15, // Porta standard per la configurazione
            priority = "NORMAL",
            confirmed = false
        )

        // 3. Inviamo al topic collettore
        // Usiamo deviceId come chiave per garantire l'ordine sequenziale
        kafkaTemplate.send("ttn-downlink-clean", deviceId, downlinkRequest)

        println("Sent Clean Downlink to Kafka: $downlinkRequest")
    }

    /**
     * Prende un payload già codificato e lo spara sul topic "clean"
     */
    fun sendDownlink(deviceId: String, encoded: EncodedPayload) {
        val request = DownlinkRequestDTO(
            deviceId = deviceId,
            rawPayload = encoded.bytes,
            fPort = encoded.fPort,
            priority = "NORMAL",
            confirmed = false
        )

        // Usiamo deviceId come chiave per l'ordine dei messaggi
        kafkaTemplate.send("ttn-downlink-clean", deviceId, request)

        println(">>> KAFKA: Comando inviato a $deviceId su fPort ${encoded.fPort}")
    }
}
