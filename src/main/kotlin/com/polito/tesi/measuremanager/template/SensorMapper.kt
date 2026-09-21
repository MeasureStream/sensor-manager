package com.polito.tesi.measuremanager.template

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.SensorDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import org.springframework.stereotype.Component

@Component
class SensorMapper(private val templateService: TemplateService) {
    /**
     * Converte un singolo Sensor in SensorDTO recuperando il template.
     * Delega a Sensor.toDTO, così un template mancante non solleva eccezioni neanche qui.
     */
    fun toSensorDTO(sensor: Sensor): SensorDTO = sensor.toDTO(templateService)

    /**
     * Converte la MeasurementUnit e mappa la lista di sensori usando il metodo sopra
     */
    fun toUnitDTO(unit: MeasurementUnit): MeasurementUnitDTO {
        return MeasurementUnitDTO(
            id = unit.id,
            extendedId = unit.extendedId,
            localId = unit.localId,
            model = unit.model,
            controlUnitId = unit.controlUnit?.id,
            sensors = unit.sensors.map { toSensorDTO(it) },
        )
    }
}
