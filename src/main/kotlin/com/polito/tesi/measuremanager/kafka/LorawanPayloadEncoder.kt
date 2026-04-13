package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CUConfigurationDTO
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
     * Comando 0x0B: Sensor Sampling Update (Porta 16)
     */
    fun encodeSensorConfig(config: CUConfigurationDTO): EncodedPayload {
        val out = ByteArrayOutputStream()

        // Header comando
        out.write(0x0B.toInt())
        // Numero di MU
        out.write(config.configurations.size)

        config.configurations.forEach { mu ->
            out.write(mu.localId)           // Indirizzo MU
            out.write(mu.sensors.size)      // Numero sensori

            mu.sensors.forEach { sensor ->
                out.write(sensor.sensorIndex)
                out.write(sensor.samplingPeriod and 0xFF)
            }
        }

        return EncodedPayload(out.toByteArray(), fPort = 16)
    }
}
