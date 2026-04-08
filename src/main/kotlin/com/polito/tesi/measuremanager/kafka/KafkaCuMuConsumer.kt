package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CuJoinNotification
import com.polito.tesi.measuremanager.dtos.CuStatusUpdate
import com.polito.tesi.measuremanager.dtos.SignalQualityUpdate
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaCUMuConsumer(private val cus: ControlUnitServiceImpl) {
    @KafkaListener(
        topics = ["cu-join-notification"],
        groupId = "measure-manager-group",
        properties = ["spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.CuJoinNotification"],
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

    @KafkaListener(
        topics = ["ttn-uplink-signal-quality"],
        groupId = "measure-manager-group",
        properties = ["spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.SignalQualityUpdate"],
    )
    fun consumeSignalQuality(dto: SignalQualityUpdate) {
        println("Ricevuto Signal Quality [${dto.devEUI}]: RSSI=${dto.rssi}, DR=${dto.dataRate}, Airtime=${dto.airtime}")
        try {
            // Qui dovrai aggiungere un metodo nel tuo ControlUnitServiceImpl per gestire questi dati
            // Esempio: cus.updateSignalStats(dto)
            cus.onSignalUpdate(dto)
        } catch (e: Exception) {
            println("ERRORE durante consumeSignalQuality: ${e.message}")
            e.printStackTrace()
        }
    }

}
