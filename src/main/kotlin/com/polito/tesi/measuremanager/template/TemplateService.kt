package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.io.File

@Service
class TemplateService(
    private val objectMapper: ObjectMapper =
        ObjectMapper().registerModule(
            KotlinModule.Builder().build(),
        ),
) {
    private val templates = mutableMapOf<String, SensorTemplate>()

    fun loadTemplates(path: String = "/app/templates") {
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            throw RuntimeException("Template folder not found: $path")
        }

        folder.listFiles { f -> f.extension == "json" }?.forEach { file ->
            val template: SensorTemplate = objectMapper.readValue(file)
            templates[template.modelName.lowercase()] = template
        }
    }

    fun getTemplate(modelName: String): SensorTemplate? {
        return templates[modelName.lowercase()]
    }

    fun getAllTemplates(): List<SensorTemplate> = templates.values.toList()

    fun loadSingleTemplate(file: File): SensorTemplate {
        val template: SensorTemplate = objectMapper.readValue(file)
        templates[template.modelName.lowercase()] = template
        return template
    }

    fun removeTemplate(fileName: String) {
        // Rimuove il template basato sul nome del file senza estensione
        val key = fileName.substringBeforeLast(".").lowercase()
        templates.remove(key)
    }
}
