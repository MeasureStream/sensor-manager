package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.EventMU
import com.polito.tesi.measuremanager.dtos.MuCreateDTO
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaMuProducer ( private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun sendMuCreate(muEventDTO: EventMU) {
        //kafkaTemplate.send("mu-creation", muCreateDTO)
        kafkaTemplate.send("mus", muEventDTO)
        println("Sent message to Kafka: $muEventDTO")
    }
}
