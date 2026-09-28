package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.LoraUplink
import com.polito.tesi.measuremanager.dtos.SignalQualityUpdate
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import com.polito.tesi.measuremanager.uplink.FPort
import com.polito.tesi.measuremanager.uplink.UplinkRouter
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

/**
 * L'ingresso degli uplink: due code, non sei.
 *
 * `lora-uplink` porta ogni messaggio della rete, qualunque sia la porta, con la chiave
 * DevEUI. La chiave conta: tutti i messaggi di una CU finiscono sulla stessa partizione e
 * restano nell'ordine in cui sono arrivati, quindi una notifica di topologia e' sempre
 * applicata prima del report che la segue. Con un topic per porta quella garanzia non
 * esisteva, e una MU nuova poteva comparire dopo le sue prime misure.
 *
 * `ttn-uplink-signal-quality` resta separato perche' non e' un payload: sono i metadati radio
 * che TTN attacca a ogni messaggio, e riguardano la rete, non il dispositivo.
 */
@Service
class KafkaUplinkConsumer(
        private val router: UplinkRouter,
        private val cus: ControlUnitServiceImpl,
) {
    private val log = LoggerFactory.getLogger(KafkaUplinkConsumer::class.java)

    @KafkaListener(
            topics = ["lora-uplink"],
            groupId = "measure-manager-group",
            properties =
                    [
                            "spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.LoraUplink"],
    )
    fun consumeUplink(dto: LoraUplink) {
        log.debug(
                "Uplink da DevEUI={} porta {} ({} caratteri)",
                dto.devEui,
                FPort.label(dto.fport),
                dto.rawPayload.length,
        )
        router.accept(dto)
    }

    @KafkaListener(
            topics = ["ttn-uplink-signal-quality"],
            groupId = "measure-manager-group",
            properties =
                    [
                            "spring.json.value.default.type=com.polito.tesi.measuremanager.dtos.SignalQualityUpdate"],
    )
    fun consumeSignalQuality(dto: SignalQualityUpdate) {
        try {
            cus.onSignalUpdate(dto)
        } catch (e: Exception) {
            log.error("Qualita' del segnale non registrata per {}: {}", dto.devEUI, e.message, e)
        }
    }
}
