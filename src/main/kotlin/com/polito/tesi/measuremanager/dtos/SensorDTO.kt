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
    val sensorTemplate: SensorTemplate, // ← qui metti tutto il template
)

fun Sensor.toDTO(templateService: TemplateService): SensorDTO {
    val template =
        templateService.getTemplate(this.modelName)
            ?: throw IllegalArgumentException("Template for ${this.modelName} not found")

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
        sensorTemplate = template,
    )
}
