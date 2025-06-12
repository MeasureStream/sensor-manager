package com.polito.tesi.measuremanager.kafka


import com.polito.tesi.measuremanager.dtos.EventMU
import org.springframework.kafka.core.KafkaTemplate

class KafkaNodeProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun sendNodeCreate(muEventDTO: EventMU) {
        //kafkaTemplate.send("mu-creation", muCreateDTO)
        kafkaTemplate.send("node-event", muEventDTO)
        println("Sent message to Kafka topic nodes-create: $muEventDTO")
    }
}