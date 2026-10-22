package com.polito.tesi.measuremanager.kafka

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

    // La vecchia sendPollingUpdate e' stata rimossa il 22 ottobre 2026: componeva a mano
    // | 0x0A | 0x00 | Polling | sulla FPort 15, cioe' con l'opcode dentro il payload e sulla
    // porta sbagliata. Era l'ultimo encoder rimasto al formato precedente alla v1.2. Adesso
    // la 0x0A la compone LorawanPayloadEncoder come tutti gli altri comandi, e parte da qui
    // con sendDownlink.

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
