package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

// This DTO is used to communicate between frontend and backend
data class MeasurementUnitDTO(
    val id: Long,
    val extendedId: Long,
    val localId: Int,
    val model: Int,
    // Torniamo solo l'ID della CU per evitare ricorsioni infinite nel JSON
    val controlUnitId: Long?,
    // Lista dei sensori collegati
    val sensors: List<SensorDTO> = emptyList()
)

fun MeasurementUnit.toDTO(templateService: TemplateService) = MeasurementUnitDTO(
    id = id,
    extendedId = extendedId,
    localId = localId,
    model = model,
    controlUnitId = controlUnit?.id,
    // Passiamo il servizio alla funzione toDTO di ogni sensore
    sensors = sensors.map { it.toDTO(templateService) }
)
