package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.template.SensorTemplate
import com.polito.tesi.measuremanager.template.TemplateService

data class SensorDTO(
    val id: Long,
    val modelName: String,
    val sensorIndex: Int,
    val physVal: Double,
    val elecVal: Double,
    val samplingF: Double,
    val phyThreshold: Double,
    val isUpperThresholdMax: Boolean,
    val isLowerThresholdMin: Boolean,
    val coeffA: Double?,
    val coeffB: Double?,
    val coeffC: Double?,
    val coeffD: Double?,
    val calDate: Long?,
    val measLocId: Long?,
    val calInitials: String?,
    /**
     * Template del modello di sensore. Se non è fra quelli caricati non si lancia
     * un'eccezione (farebbe fallire l'intera lista delle CU): arriva un segnaposto
     * con il solo modelName e type vuoto, e [templateResolved] vale false.
     */
    val sensorTemplate: SensorTemplate,
    /** False se il template di [modelName] non è stato trovato. */
    val templateResolved: Boolean = true,
    /**
     * False se il sensore è oltre il limite di MAX_SENSORS_PER_CU (48) per la CU:
     * resta visibile in UI ma non è configurabile (campionamento forzato a OFF).
     */
    val configurable: Boolean = true,
)

/** Segnaposto per un sensore il cui template non è caricato: la UI lo mette nel gruppo "Altro". */
private fun unresolvedTemplate(modelName: String) = SensorTemplate(modelName = modelName, type = "")

fun Sensor.toDTO(templateService: TemplateService, configurable: Boolean = true): SensorDTO {
    val template = templateService.getTemplate(this.modelName)

    return SensorDTO(
        id = this.id,
        modelName = this.modelName,
        sensorIndex = this.sensorIndex,
        physVal = this.physVal,
        elecVal = this.elecVal,
        samplingF = this.samplingF,
        phyThreshold = this.phyThreshold,
        isUpperThresholdMax = this.isUpperThresholdMax,
        isLowerThresholdMin = this.isLowerThresholdMin,
        coeffA = this.coeffA,
        coeffB = this.coeffB,
        coeffC = this.coeffC,
        coeffD = this.coeffD,
        calDate = this.calDate,
        measLocId = this.measLocId,
        calInitials = this.calInitials,
        sensorTemplate = template ?: unresolvedTemplate(this.modelName),
        templateResolved = template != null,
        configurable = configurable,
    )
}
