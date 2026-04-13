package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CUConfigurationDTO
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class LorawanPayloadEncoder {


    private val MS_IN_S = 1000L
    private val MS_IN_M = 60 * 1000L
    private val MS_IN_H = 60 * 60 * 1000L

    fun encodeSamplingPeriod(ms: Long): Int {
        return when {
            ms <= 0 -> 0 // Off
            ms <= 9 -> ms.toInt()
            ms <= 95 -> (10 + (ms - 10) / 5).toInt()
            ms <= 950 -> (28 + (ms - 100) / 50).toInt()
            ms <= 9 * MS_IN_S -> (46 + (ms - 1 * MS_IN_S) / MS_IN_S).toInt()
            ms <= 55 * MS_IN_S -> (55 + (ms - 10 * MS_IN_S) / (5 * MS_IN_S)).toInt()
            ms <= 9 * MS_IN_M -> (65 + (ms - 1 * MS_IN_M) / MS_IN_M).toInt()
            ms <= 55 * MS_IN_M -> (74 + (ms - 10 * MS_IN_M) / (5 * MS_IN_M)).toInt()
            ms <= 24 * MS_IN_H -> (84 + (ms - 1 * MS_IN_H) / (10 * MS_IN_M)).toInt()
            ms <= 48 * MS_IN_H -> (223 + (ms - 25 * MS_IN_H) / MS_IN_H).toInt()
            else -> 255
        }
    }

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
        out.write(0x0B.toInt())
        out.write(config.configurations.size)

        config.configurations.forEach { mu ->
            out.write(mu.localId)
            out.write(mu.sensors.size)
            mu.sensors.forEach { sensor ->
                out.write(sensor.sensorIndex)
                // QUI LA MODIFICA: convertiamo i ms del DTO nell'indice del firmware
                val index = encodeSamplingPeriod(sensor.samplingPeriod.toLong())
                out.write(index and 0xFF)
            }
        }
        return EncodedPayload(out.toByteArray(), fPort = 0x21)
    }
}
