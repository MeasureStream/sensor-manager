package com.polito.tesi.measuremanager.template

import com.polito.tesi.measuremanager.entities.TemplateKind
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.io.File

/**
 * Importa la cartella seme nel registro al primo avvio, una volta sola.
 *
 * Sostituisce il vecchio TemplateLoader con il suo watcher: la cartella e' montata in sola
 * lettura e resta il seme, mentre gli aggiornamenti passano dalle API. Import idempotente:
 * un documento gia' presente con la stessa impronta non viene riscritto, una versione
 * pubblicata che differisce dal file non viene toccata (vince il registro).
 */
@Component
class TemplateSeedImporter(
        private val registry: TemplateRegistry,
        @Value("\${measurestream.templates.seed-path:/app/templates}")
        private val seedPath: String,
) {
    private val logger = LoggerFactory.getLogger(TemplateSeedImporter::class.java)

    /** La cartella del repository dei template: il nome dice il tipo dei documenti dentro. */
    private val kindByFolder =
            mapOf(
                    "sensors" to TemplateKind.SENSOR,
                    "references" to TemplateKind.REFERENCE,
                    "mu" to TemplateKind.MU,
                    "cu" to TemplateKind.CU,
                    "protocol" to TemplateKind.PROTOCOL,
            )

    @PostConstruct
    fun importSeed() {
        val folder = File(seedPath)
        if (!folder.isDirectory) {
            logger.warn("Cartella seme assente: {} — il registro parte con cio' che ha", seedPath)
            registry.warmUp()
            return
        }

        var imported = 0
        var skipped = 0
        folder.walkTopDown()
                .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                .forEach { file ->
                    try {
                        registry.publish(
                                json = file.readText(),
                                source = "seed",
                                publishedBy = null,
                                kindHint = kindFor(file, folder),
                        )
                        imported++
                    } catch (e: Exception) {
                        skipped++
                        // Un file rotto non deve impedire il caricamento degli altri.
                        logger.warn("Template seme ignorato {}: {}", file.name, e.message)
                    }
                }

        registry.warmUp()
        logger.info("Seme dei template: {} importati, {} ignorati", imported, skipped)
    }

    /**
     * Il tipo viene dalla cartella; un campo `kind` dentro al documento ha comunque la
     * precedenza. I file rimasti nella radice si assumono sensori, com'erano prima della
     * riorganizzazione del repository.
     */
    private fun kindFor(file: File, root: File): TemplateKind {
        val relative = file.parentFile?.relativeTo(root)?.path?.replace('\\', '/') ?: ""
        val top = relative.substringBefore('/')
        return kindByFolder[top] ?: TemplateKind.SENSOR
    }
}
