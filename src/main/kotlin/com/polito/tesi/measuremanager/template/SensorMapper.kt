package com.polito.tesi.measuremanager.template

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.SensorDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import org.springframework.stereotype.Component

@Component
class SensorMapper(
    private val templateService: TemplateService,
    private val protocolService: ProtocolService,
) {
    /**
     * Converte un singolo Sensor in SensorDTO recuperando il template.
     * Delega a Sensor.toDTO, così un template mancante non solleva eccezioni neanche qui.
     */
    fun toSensorDTO(sensor: Sensor): SensorDTO = sensor.toDTO(templateService)

    /**
     * Converte la MeasurementUnit delegando alla stessa `toDTO` usata dentro la CU: una MU
     * vista da sola e una vista dentro la sua CU devono avere la stessa forma, altrimenti
     * l'interfaccia trova campi diversi a seconda di dove ha chiesto.
     */
    fun toUnitDTO(unit: MeasurementUnit): MeasurementUnitDTO =
        unit.toDTO(templateService, protocol = protocolService)
}
