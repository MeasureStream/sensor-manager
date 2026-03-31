package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.MuRegistrationDTO
import com.polito.tesi.measuremanager.services.MeasurementUnitServiceImpl
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaCUMuConsumer(private val measurementUnitService: MeasurementUnitServiceImpl) {
    @KafkaListener(
        topics = ["mu-registration"],
        groupId = "measure-manager-group",
        properties = ["spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.MuRegistrationDTO"],
    )
    fun consumeMuRegistration(dto: MuRegistrationDTO) {
        println("Ricevuto DTO: $dto")
        try {
            measurementUnitService.registerMu(
                muNetworkId = dto.muId.toLong(),
                cuNetworkId = dto.cuId.toLong(),
                muModel = dto.modelMu.toInt(),
            )
        } catch (e: Exception) {
            println("ERRORE durante l'elaborazione del messaggio: ${e.message}")
            e.printStackTrace()
        }
    }
}
