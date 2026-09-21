package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit

data class SensorConfigDTO(
    val sensorIndex: Int,       // ID del sensore (es. 1 per Temp, 2 per Hum)
    val samplingPeriod: Int  // Periodo in secondi
)

data class MUConfigCommandDTO(
    val localId: Int,      // Indirizzo della MU (0, 1, 2...)
    val sensors: List<SensorConfigDTO>
)

data class CUConfigurationDTO(
    val devEui: String,    // ID della Control Unit su TTN
    val configurations: List<MUConfigCommandDTO>
)

data class CUTransmissionCommandDTO(
    val devEui: String,
    val transmissionIndex: Int // 0 = STOP, 1-246 = START (intervallo codificato)
)

fun ControlUnit.toCUTransmissionCommandDTO() = CUTransmissionCommandDTO(devEui.toString(),transmissionInterval)
