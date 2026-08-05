package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.data.geo.Point

/**
 * Soglia minima (in minuti) sotto la quale una CU non viene mai considerata offline,
 * per tollerare ritardi di rete/TTN anche con intervalli di trasmissione brevi.
 */
private const val MIN_OFFLINE_THRESHOLD_MINUTES = 30L

/**
 * Una CU è online se ha contattato TTN di recente.
 * "Di recente" = entro 2 volte il transmissionInterval (1 step = 15 min, 255 = 1 min),
 * con un minimo di [MIN_OFFLINE_THRESHOLD_MINUTES].
 * NB: lastSeen è salvato come orario UTC (parse del timestamp TTN), quindi il confronto
 * va fatto con l'ora UTC corrente.
 */
fun ControlUnit.isOnline(): Boolean {
    val seen = lastSeen ?: return false
    val intervalMinutes = when (transmissionInterval) {
        in 1..254 -> transmissionInterval * 15L
        255 -> 1L
        else -> 0L // trasmissione OFF: resta valida solo la soglia minima
    }
    val thresholdMinutes = maxOf(MIN_OFFLINE_THRESHOLD_MINUTES, intervalMinutes * 2)
    return seen.isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(thresholdMinutes))
}

data class ControlUnitDTO(
    val id: Long,
    val devEui: String,           // Identificativo hardware reale
    val deviceId: String,
    @field:NotBlank(message = "Name is mandatory")
    val name: String,
    @field:PositiveOrZero
    @field:Max(value = 100, message = "Remaining battery must be under 100")
    val remainingBattery: Double,
    val acPowered : Boolean,
    val isCharging : Boolean,
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

    val lastSeen: LocalDateTime?,
    val usedDailyAirtime: Long,
    val lastAirtime: Double,

    /** Ultimo f_cnt LoRaWAN ricevuto da TTN per questa CU (null = mai ricevuto). */
    val lastFCnt: Int?,

    val transmissionInterval: Int,

    // Lista delle MU collegate (solo gli ID o gli ExtendedID per leggerezza)
    val measurementUnits: List<MeasurementUnitDTO> = listOf(),

)

fun ControlUnit.toDTO(templateService: TemplateService) = ControlUnitDTO(
    id = id,
    devEui = devEui.toString(),
    deviceId = deviceId,
    name = name,
    remainingBattery = remainingBattery,
    acPowered = acPowered,
    isCharging = isCharging,
    rssi = rssi,
    model = model,
    // Stato derivato da lastSeen: unica fonte di verità per landing e pagina di dettaglio.
    // (il campo status dell'entità non veniva mai aggiornato: restava sempre 0)
    status = if (isOnline()) 1 else 0,
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
    lastSeen = lastSeen,
    usedDailyAirtime = usedDailyAirtime,
    lastAirtime = lastAirtime,
    transmissionInterval = transmissionInterval,

    lastFCnt = lastFCnt,

    // MU in ordine di localId, contando i sensori in modo cumulativo:
    // i sensori oltre MAX_SENSORS_PER_CU (48) vengono marcati configurable = false
    measurementUnits = run {
        var sensorOffset = 0
        measurementUnits.sortedBy { it.localId }.map { mu ->
            val dto = mu.toDTO(templateService, sensorOffset)
            sensorOffset += mu.sensors.size
            dto
        }
    }
)
