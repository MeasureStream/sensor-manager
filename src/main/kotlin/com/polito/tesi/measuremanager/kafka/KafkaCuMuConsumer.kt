package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.MuRegistrationDTO
import com.polito.tesi.measuremanager.services.MeasurementUnitServiceImpl
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaCUMuConsumer(private val measurementUnitService: MeasurementUnitServiceImpl) {
    @KafkaListener(topics = ["mu-registration"])
    fun consumeMuRegistration(dto: MuRegistrationDTO) {
        measurementUnitService.registerMu(
            muNetworkId = dto.muId.toLong(),
            cuNetworkId = dto.cuId.toLong(),
            muModel = dto.modelMu.toInt()
        )
    }
}
