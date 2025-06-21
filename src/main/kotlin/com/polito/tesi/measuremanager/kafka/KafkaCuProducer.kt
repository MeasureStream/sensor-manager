package com.polito.tesi.measuremanager.kafka



import com.polito.tesi.measuremanager.dtos.EventCU
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaCuProducer( private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun sendCuCreate(event: EventCU) {
        //kafkaTemplate.send("cu-creation", cuCreateDTO)
        kafkaTemplate.send("cu-creation", event)
        println("Sent message to Kafka topic: cu-creation : $event")
    }
}