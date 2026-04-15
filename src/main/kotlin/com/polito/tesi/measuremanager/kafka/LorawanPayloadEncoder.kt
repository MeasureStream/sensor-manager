package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CUConfigurationDTO
import com.polito.tesi.measuremanager.dtos.CUTransmissionCommandDTO
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class LorawanPayloadEncoder {


    /**
     * Comando 0x0A: Polling Update (Porta 15)
     */
    fun encodePollingUpdate(interval: Int): EncodedPayload {
        val bytes = byteArrayOf(
            0x0A.toByte(),
            0x00.toByte(),
            (interval and 0xFF).toByte()
        )
        return EncodedPayload(bytes, fPort = 15)
    }

    /**
     * Comando 0x0B: Sensor Sampling Update (Fport 0x21)
     * Costruisce il payload ordinando MU e Sensori come richiesto dal firmware.
     */
    fun encodeSensorConfig(config: CUConfigurationDTO): EncodedPayload {
        val out = ByteArrayOutputStream()

        // 1. Identificativo del comando
        out.write(0x21)


        // Ordiniamo le MU per localId prima di scriverle
        config.configurations
            .sortedBy { it.localId }
            .forEach { mu ->
                // Ordiniamo i sensori per sensorIndex prima di scriverli
                mu.sensors
                    .sortedBy { it.sensorIndex }
                    .forEach { sensor ->

                        // Periodo di campionamento (Indice 0-256 ricevuto dal frontend)

                        out.write(sensor.samplingPeriod and 0xFF)
                    }
            }

        // Ritorna il payload sulla porta fPort 33 (0x21)
        return EncodedPayload(out.toByteArray(), fPort = 33)
    }

    /**
     * Comando 0x0B: Sensor Sampling Update (Fport 0x21)
     * Costruisce il payload ordinando MU e Sensori come richiesto dal firmware.
     */
    fun encodeTransmissionConfig(config: CUTransmissionCommandDTO): EncodedPayload {
        val out = ByteArrayOutputStream()

        // 1. Identificativo del comando
        out.write(0x22)

        out.write(config.transmissionIndex and 0xFF)
        // Ritorna il payload sulla porta fPort 33 (0x21)
        return EncodedPayload(out.toByteArray(), fPort = 0x22)
    }
}
