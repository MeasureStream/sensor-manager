package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.data.geo.Point

data class ControlUnitDTO(
    val id: Long,
    val devEui: Long,           // Identificativo hardware reale

    @field:NotBlank(message = "Name is mandatory")
    val name: String,
    @field:PositiveOrZero
    @field:Max(value = 100, message = "Remaining battery must be under 100")
    val remainingBattery: Double,
    @field:NegativeOrZero(message = "RSSI must be negative")
    val rssi: Double,
    val model: Int,
    val status: Int,
    val dataRate: Int,
    val usedDC: Int,
    val hasGPS: Boolean,
    val location: Point?,
    val maxMU: Int,
    // Parametri di configurazione (Settings)
    val setting1: Int,
    val transmissionPower: Int,
    val pollingInterval: Int,
    val semanticLocation: String,
    // Parametri Radio
    val bandwidth: Int,
    val spreadingFactor: Int,
    val codingRate: String,
    val frequency: Int,
    // Lista delle MU collegate (solo gli ID o gli ExtendedID per leggerezza)
    val measurementUnits: List<MeasurementUnitDTO> = listOf()
)

fun ControlUnit.toDTO(templateService: TemplateService) = ControlUnitDTO(
    id = id,
    devEui = devEui,

    name = name,
    remainingBattery = remainingBattery,
    rssi = rssi,
    model = model,
    status = status,
    dataRate = dataRate,
    usedDC = usedDC,
    hasGPS = hasGPS,
    location = location,
    maxMU = MaxMU,
    setting1 = setting1,
    transmissionPower = transmissionPower,
    pollingInterval = pollingInterval,
    semanticLocation = semanticLocation,
    bandwidth = bandwidth,
    spreadingFactor = spreadingFactor,
    codingRate = codingRate,
    frequency = frequency,
    // Mappiamo solo gli ID delle MU collegate
    measurementUnits = measurementUnits.map { it.toDTO(templateService) }
)
