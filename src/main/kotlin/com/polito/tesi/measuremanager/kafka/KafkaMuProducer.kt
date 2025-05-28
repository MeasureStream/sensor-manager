package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.MuCreateDTO
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaMuProducer ( private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun sendMuCreate(muCreateDTO: MuCreateDTO) {
        kafkaTemplate.send("mu-creation", muCreateDTO)
        println("Sent message to Kafka: $muCreateDTO")
    }
}
