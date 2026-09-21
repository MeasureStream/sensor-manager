package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Vista «template di sensore» sul registro.
 *
 * Esiste per non cambiare i chiamanti (DTO e servizi) mentre la fonte passa dai file al
 * registro: la firma [getTemplate] resta quella di prima. Il passo 8 sostituira' il nome
 * del modello con il riferimento `(templateId, MAJOR)` e questa classe sparira'.
 */
@Service
class TemplateService(
        private val registry: TemplateRegistry,
        private val objectMapper: ObjectMapper,
) {
    /** Le versioni sono immutabili: la deserializzazione si fa una volta per impronta. */
    private val parsed = ConcurrentHashMap<String, SensorTemplate>()

    fun getTemplate(modelName: String): SensorTemplate? =
            registry.resolveByModelName(modelName)?.let { toSensorTemplate(it) }

    fun getAllTemplates(): List<SensorTemplate> =
            registry.list(TemplateKind.SENSOR).mapNotNull { toSensorTemplate(it) }

    /** Il documento completo, quando servono i campi che [SensorTemplate] non porta. */
    fun getDocument(modelName: String): TemplateRecord? = registry.resolveByModelName(modelName)

    private fun toSensorTemplate(record: TemplateRecord): SensorTemplate? =
            parsed.getOrPut(record.contentHash) {
                objectMapper.convertValue(record.content, SensorTemplate::class.java)
            }
}
