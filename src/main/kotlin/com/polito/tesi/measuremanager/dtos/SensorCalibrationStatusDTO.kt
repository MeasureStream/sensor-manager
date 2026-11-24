package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.ActiveCalibration
import com.polito.tesi.measuremanager.entities.MetricSample
import com.polito.tesi.measuremanager.quality.QualityFlags
import java.time.OffsetDateTime

/**
 * Lo stato di taratura di un sensore come lo vede il server che converte: con quale funzione
 * si stanno convertendo adesso le sue misure, da quando, fino a quando, e con che incertezza.
 *
 * E' la risposta alla domanda «questo sensore, in campo, e' tarato?» senza aprire il database:
 * lo stato e' ricavato dalla stessa risoluzione che usa la decodifica dei report, quindi dice
 * cio' che sta succedendo davvero, non cio' che un certificato prometteva.
 */
data class SensorCalibrationStatusDTO(
        val sensorId: Long,
        val sensorIndex: Int,
        val modelName: String,
        val channel: String? = null,
        /** Falso per i sensori fuori dal perimetro di taratura (template `requiresCalibration`). */
        val requiresCalibration: Boolean = true,
        /**
         * `INSTANCE` taratura dell'esemplare in vigore; `EXPIRED` scaduta ma ancora in uso (la
         * funzione resta la migliore nota, i campioni prendono CAL_EXPIRED); `NOMINAL` formula
         * di targa del modello; `NOT_REQUIRED` fuori dal perimetro.
         */
        val state: String,
        val current: ActiveCalibrationDTO? = null,
        /** Tutte le tarature ricevute per questo esemplare, dalla piu' recente. */
        val history: List<ActiveCalibrationDTO> = emptyList(),
        /** L'ultimo valore convertito, con la sua incertezza e i suoi flag. */
        val lastSample: SampleQualityDTO? = null,
)

data class ActiveCalibrationDTO(
        val id: Long,
        val calibratedItemId: Long?,
        val certificateId: Long,
        val certificateNumber: String?,
        val revision: Int,
        val functionType: String?,
        val validFrom: OffsetDateTime,
        val validUntil: OffsetDateTime?,
        val revokedAt: OffsetDateTime?,
        val calibratedMin: Double?,
        val calibratedMax: Double?,
        val receivedAt: OffsetDateTime,
)

data class SampleQualityDTO(
        val metric: String,
        val timestamp: OffsetDateTime,
        val windowStart: OffsetDateTime?,
        val windowEnd: OffsetDateTime?,
        val value: Double?,
        val converted: Boolean?,
        /** Incertezza tipo (k = 1). */
        val uStd: Double?,
        /** Incertezza estesa, k = 2. */
        val expandedU: Double?,
        val uComponents: Map<String, Double>?,
        val qualityFlags: Int?,
        val flagNames: List<String>,
        val certificateId: Long?,
        val certificateNumber: String?,
        val templateVersion: String?,
)

fun ActiveCalibration.toDTO() =
        ActiveCalibrationDTO(
                id = id,
                calibratedItemId = calibratedItemId,
                certificateId = certificateId,
                certificateNumber = certificateNumber,
                revision = revision,
                functionType = function["type"] as? String,
                validFrom = validFrom,
                validUntil = validUntil,
                revokedAt = revokedAt,
                calibratedMin = calibratedMin,
                calibratedMax = calibratedMax,
                receivedAt = receivedAt,
        )

fun MetricSample.toQualityDTO() =
        SampleQualityDTO(
                metric = metric,
                timestamp = timestamp,
                windowStart = windowStart,
                windowEnd = windowEnd,
                value = value,
                converted = converted,
                uStd = uStd,
                expandedU = uStd?.let { 2 * it },
                uComponents = uComponents,
                qualityFlags = qualityFlags,
                flagNames = QualityFlags.names(qualityFlags),
                certificateId = calibration?.certificateId,
                certificateNumber = calibration?.certificateNumber,
                templateVersion = templateVersion,
        )
