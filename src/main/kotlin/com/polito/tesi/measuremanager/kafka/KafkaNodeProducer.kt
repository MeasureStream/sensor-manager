package com.polito.tesi.measuremanager.kafka



import com.polito.tesi.measuremanager.dtos.EventNode
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaNodeProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun sendNodeCreate(event: EventNode) {
        //kafkaTemplate.send("mu-creation", muCreateDTO)
        kafkaTemplate.send("node-event", event)
        println("Sent message to Kafka topic nodes-event: $event")
    }
}