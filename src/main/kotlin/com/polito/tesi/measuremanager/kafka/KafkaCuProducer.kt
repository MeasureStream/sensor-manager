package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CUConfigCommandDTO
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
        // Usiamo deviceId come chiave per garantire l'ordine dei messaggi su Kafka per la stessa CU
        kafkaTemplate.send("cu-configuration", deviceId, event)
        println("Sent polling update to Kafka topic: cu-configuration for device $deviceId: $event")
    }
}
