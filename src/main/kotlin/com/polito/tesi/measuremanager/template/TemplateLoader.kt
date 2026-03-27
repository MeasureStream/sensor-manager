package com.polito.tesi.measuremanager.template

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.nio.file.*

@Component
class TemplateLoader(
    private val templateService: TemplateService
) {
    private val templatesPath: String = "/app/templates"

    @PostConstruct
    fun init() {
        // Carica tutti i template all'avvio
        templateService.loadTemplates(templatesPath)
        println("Loaded templates: ${templateService.getAllTemplates().size}")

        // Avvia il watcher per hot-reload incrementale
        watchTemplates()
    }

    private fun watchTemplates() {
        val watchService = FileSystems.getDefault().newWatchService()
        val folder = Paths.get(templatesPath)
        folder.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        )

        Thread {
            while (true) {
                val key = watchService.take()
                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    val fileName = event.context() as Path
                    val fullPath = folder.resolve(fileName).toFile()

                    when (kind) {
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY -> {
                            if (fullPath.extension.lowercase() == "json") {
                                try {
                                    val template = templateService.loadSingleTemplate(fullPath)
                                    println("Template reloaded: ${template.modelName}")
                                } catch (ex: Exception) {
                                    println("Failed to reload template ${fileName}: ${ex.message}")
                                }
                            }
                        }

                        StandardWatchEventKinds.ENTRY_DELETE -> {
                            templateService.removeTemplate(fileName.toString())
                            println("Template removed: $fileName")
                        }
                    }
                }
                key.reset()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}