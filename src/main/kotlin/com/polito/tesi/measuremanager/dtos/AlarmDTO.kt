package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.SensorAlarm
import java.time.OffsetDateTime

/** Un allarme, con le condizioni gia' risolte in testo. */
data class AlarmDTO(
        val id: Long,
        val receivedAt: OffsetDateTime,
        val localId: Int,
        val sensorIndex: Int,
        val sensorId: Long?,
        val sensorModel: String?,
        /** False = condizione attivata, true = rientrata. */
        val cleared: Boolean,
        val conditions: List<Map<String, Any?>>,
        val rawValue: Int?,
        val value: Double?,
        val acknowledged: Boolean,
)

/**
 * Un evento. [occurredAt] e' ricavato dall'eta' dichiarata, quindi porta con se'
 * [ageUncertaintySeconds]: e' l'incertezza della fascia di codifica, non un dettaglio.
 */
data class DeviceEventDTO(
        val id: Long,
        val receivedAt: OffsetDateTime,
        val occurredAt: OffsetDateTime?,
        val ageUncertaintySeconds: Int?,
        val source: Int,
        val sensorIndex: Int,
        val sensorId: Long?,
        val code: Int,
        val param: Int?,
        val description: String?,
        val acknowledged: Boolean,
)

fun SensorAlarm.toDTO() =
        AlarmDTO(
                id = id,
                receivedAt = receivedAt,
                localId = localId,
                sensorIndex = sensorIndex,
                sensorId = sensor?.id,
                sensorModel = sensor?.modelName,
                cleared = cleared,
                conditions = conditions ?: emptyList(),
                rawValue = rawValue,
                value = value,
                acknowledged = acknowledgedAt != null,
        )

fun DeviceEvent.toDTO() =
        DeviceEventDTO(
                id = id,
                receivedAt = receivedAt,
                occurredAt = occurredAt,
                ageUncertaintySeconds = ageUncertaintySeconds,
                source = source,
                sensorIndex = sensorIndex,
                sensorId = sensor?.id,
                code = code,
                param = param,
                description = description,
                acknowledged = acknowledgedAt != null,
        )
