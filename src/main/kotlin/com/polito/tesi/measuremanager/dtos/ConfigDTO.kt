package com.polito.tesi.measuremanager.dtos

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
