package com.polito.tesi.measuremanager.template


import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.SensorDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.template.TemplateService
import org.springframework.stereotype.Component

@Component
class SensorMapper(private val templateService: TemplateService) {

    /**
     * Converte un singolo Sensor in SensorDTO recuperando il template
     */
    fun toSensorDTO(sensor: Sensor): SensorDTO {
        val template = templateService.getTemplate(sensor.modelName)
            ?: throw IllegalArgumentException("Template for ${sensor.modelName} not found")

        return SensorDTO(
            id = sensor.id,
            modelName = sensor.modelName,
            sensorIndex = sensor.sensorIndex,
            physVal = sensor.physVal,
            elecVal = sensor.elecVal,
            samplingF = sensor.samplingF,
            phyThreshold = sensor.phyThreshold,
            isUpperThresholdMax = sensor.isUpperThresholdMax,
            isLowerThresholdMin = sensor.isLowerThresholdMin,
            coeffA = sensor.coeffA,
            coeffB = sensor.coeffB,
            coeffC = sensor.coeffC,
            coeffD = sensor.coeffD,
            calDate = sensor.calDate,
            measLocId = sensor.measLocId,
            calInitials = sensor.calInitials,
            sensorTemplate = template
        )
    }

    /**
     * Converte la MeasurementUnit e mappa la lista di sensori usando il metodo sopra
     */
    fun toUnitDTO(unit: MeasurementUnit): MeasurementUnitDTO {
        return MeasurementUnitDTO(
            id = unit.id,
            networkId = unit.networkId,
            model = unit.model,
            nodeId = unit.node?.id,
            sensors = unit.sensors.map { toSensorDTO(it) }
        )
    }
}