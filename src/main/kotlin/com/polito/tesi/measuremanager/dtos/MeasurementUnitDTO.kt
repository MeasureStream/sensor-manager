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

/**
 * @param sensorOffset numero di sensori della stessa CU già contati prima di questa MU
 * (in ordine di localId): serve a marcare come non configurabili i sensori oltre
 * il limite di MAX_SENSORS_PER_CU. Con il default 0 (MU fuori dal contesto CU)
 * tutti i sensori risultano configurabili (max 12 per MU < 48).
 */
fun MeasurementUnit.toDTO(templateService: TemplateService, sensorOffset: Int = 0) = MeasurementUnitDTO(
    id = id,
    extendedId = extendedId,
    localId = localId,
    model = model,
    controlUnitId = controlUnit?.id,
    // Passiamo il servizio alla funzione toDTO di ogni sensore.
    // I sensori vengono contati in ordine di sensorIndex: quelli oltre il 48° della CU
    // sono marcati configurable = false.
    sensors = sensors.sortedBy { it.sensorIndex }.mapIndexed { i, s ->
        s.toDTO(templateService, configurable = sensorOffset + i < MAX_SENSORS_PER_CU)
    }
)
