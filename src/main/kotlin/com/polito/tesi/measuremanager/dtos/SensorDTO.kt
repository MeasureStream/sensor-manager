package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.polito.tesi.measuremanager.entities.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type" // Questo campo apparirà nel JSON per distinguere i sensori
)
@JsonSubTypes(
    JsonSubTypes.Type(value = NTCSensorDTO::class, name = "NTC"),
    JsonSubTypes.Type(value = PressureSensorDTO::class, name = "PRESSURE"),
    JsonSubTypes.Type(value = HumiditySensorDTO::class, name = "HUMIDITY"),
    JsonSubTypes.Type(value = AccelerometerSensorDTO::class, name = "ACCELEROMETER")
)
abstract class SensorDTO(
    open val id: Long?,
    open val modelName: String,
    open val sensorIndex: Int
)

// --- Implementazioni Concrete ---

data class NTCSensorDTO(
    override val id: Long?,
    override val modelName: String = "NTC",
    override val sensorIndex: Int = 4,
    val templateId: Int = 201,
    val unitCode: Int = 1,
    val minPhysVal: Double,
    val maxPhysVal: Double,
    val minElecVal: Double,
    val maxElecVal: Double,
    val coeffA: Double,
    val coeffB: Double,
    val coeffC: Double,
    val calDate: Long,
    val calPeriod: Int,
    val calInitials: String?,
    val refResOhm: Double,
    val refTempC: Double,
    val responseTime: Double,
    val selfHeat: Double,
    val exciteNom: Double,
    val exciteMax: Double,
    val measLocId: Long
) : SensorDTO(id, modelName, sensorIndex)

data class PressureSensorDTO(
    override val id: Long?,
    override val modelName: String = "MS5837",
    override val sensorIndex: Int = 2,
    val templateId: Int = 202,
    val unitCode: Int = 2,
    val coeffC1: Int,
    val coeffC2: Int,
    val coeffC3: Int,
    val coeffC4: Int,
    val coeffC5: Int,
    val coeffC6: Int,
    val minPressure: Double,
    val maxPressure: Double,
    val calDate: Long,
    val fluidDensity: Double
) : SensorDTO(id, modelName, sensorIndex)

data class HumiditySensorDTO(
    override val id: Long?,
    override val modelName: String = "HPP845E",
    override val sensorIndex: Int = 3,
    val templateId: Int = 203,
    val unitCode: Int = 3,
    val coeffA: Double,
    val coeffB: Double,
    val coeffC: Double,
    val minRH: Double,
    val maxRH: Double,
    val responseTime: Double,
    val calDate: Long
) : SensorDTO(id, modelName, sensorIndex)

data class AccelerometerSensorDTO(
    override val id: Long?,
    override val modelName: String = "LSM6DSM",
    override val sensorIndex: Int = 1,
    val templateId: Int = 204,
    val unitCode: Int = 4,
    val sensitivityX: Double,
    val sensitivityY: Double,
    val sensitivityZ: Double,
    val biasX: Double,
    val biasY: Double,
    val biasZ: Double,
    val fullScaleRange: Int,
    val outputDataRate: Double,
    val calDate: Long
) : SensorDTO(id, modelName, sensorIndex)



fun Sensor.toDTO(): SensorDTO {
    return when (this) {
        is NTCSensor -> NTCSensorDTO(
            id, modelName, sensorIndex, templateId, unitCode, minPhysVal, maxPhysVal,
            minElecVal, maxElecVal, coeffA, coeffB, coeffC, calDate, calPeriod,
            calInitials, refResOhm, refTempC, responseTime, selfHeat, exciteNom, exciteMax, measLocId
        )
        is PressureSensor -> PressureSensorDTO(
            id, modelName, sensorIndex, templateId, unitCode, coeffC1, coeffC2, coeffC3,
            coeffC4, coeffC5, coeffC6, minPressure, maxPressure, calDate, fluidDensity
        )
        is HumiditySensor -> HumiditySensorDTO(
            id, modelName, sensorIndex, templateId, unitCode, coeffA, coeffB, coeffC,
            minRH, maxRH, responseTime, calDate
        )
        is AccelerometerSensor -> AccelerometerSensorDTO(
            id, modelName, sensorIndex, templateId, unitCode, sensitivityX, sensitivityY,
            sensitivityZ, biasX, biasY, biasZ, fullScaleRange, outputDataRate, calDate
        )
        else -> throw IllegalArgumentException("Unknown sensor type")
    }
}