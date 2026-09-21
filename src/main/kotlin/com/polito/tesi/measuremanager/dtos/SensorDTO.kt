package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.template.TemplateService

data class SensorDTO(
    val id: Long,
    val modelName: String,
    val sensorIndex: Int,
    /** Asse dello slot ("X", "Y", "Z"), quando il modello di MU ne dichiara uno. */
    val channel: String? = null,
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
     * Riferimento al template, non il template intero: il documento si scarica una volta
     * sola da /API/templates. Se il registro non ha quel modello vale null — nessuna
     * eccezione, che farebbe fallire l'intera lista delle CU — e la UI mette il sensore
     * nel gruppo "Altro".
     */
    val template: TemplateRefDTO?,
    /** Comodità per la UI: equivale a `template != null`. */
    val templateResolved: Boolean = true,
    /**
     * False se il sensore è oltre il limite di MAX_SENSORS_PER_CU (48) per la CU:
     * resta visibile in UI ma non è configurabile (campionamento forzato a OFF).
     */
    val configurable: Boolean = true,
)

fun Sensor.toDTO(templateService: TemplateService, configurable: Boolean = true): SensorDTO {
    // Il record dice identificativo, MAJOR e versione risolta; il documento i due campi
    // che servono alla card. Entrambi arrivano dalla cache del registro, senza query.
    val record = templateService.getDocument(this.modelName)
    val ref = record?.let { templateRef(it, templateService.getTemplate(this.modelName)) }

    return SensorDTO(
        id = this.id,
        modelName = this.modelName,
        sensorIndex = this.sensorIndex,
        channel = this.channel,
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
        template = ref,
        templateResolved = ref != null,
        configurable = configurable,
    )
}
