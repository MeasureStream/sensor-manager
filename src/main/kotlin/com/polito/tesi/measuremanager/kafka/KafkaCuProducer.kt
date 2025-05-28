package com.polito.tesi.measuremanager.kafka


import com.polito.tesi.measuremanager.dtos.CuCreateDTO
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaCuProducer( private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun sendCuCreate(cuCreateDTO: CuCreateDTO) {
        kafkaTemplate.send("cu-creation", cuCreateDTO)
        println("Sent message to Kafka: $cuCreateDTO")
    }
}