package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CuJoinNotification
import com.polito.tesi.measuremanager.dtos.CuStatusUpdate
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaCUMuConsumer(private val cus: ControlUnitServiceImpl) {
    @KafkaListener(
        topics = ["cu-registration"],
        groupId = "measure-manager-group",
        properties = ["spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.MuRegistrationDTO"],
    )
    fun consumeMuRegistration(dto: CuJoinNotification) {
        println("Ricevuto DTO: $dto")
        try {
            cus.onJoinNotification(dto)
        } catch (e: Exception) {
            println("ERRORE durante l'elaborazione del messaggio: ${e.message}")
            e.printStackTrace()
        }
    }


    @KafkaListener(
        topics = ["cu-status"],
        groupId = "measure-manager-group",
        properties = ["spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.CuStatusUpdate"],
    )
    fun consumeCuStatus(dto: CuStatusUpdate) {
        println("Ricevuto Status Update: $dto")
        try {
            cus.onStatusUpdate(dto)
        } catch (e: Exception) {
            println("ERRORE durante onStatusUpdate: ${e.message}")
            e.printStackTrace()
        }
    }

}
