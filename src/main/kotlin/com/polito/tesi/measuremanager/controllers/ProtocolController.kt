package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.TemplateDTO
import com.polito.tesi.measuremanager.entities.TemplateRecord
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.TemplateRegistry
import org.springframework.web.bind.annotation.*

/**
 * Il dizionario di protocollo: codifiche dei periodi, sentinelle, e piu' avanti bit di stato
 * e codici evento. Lo legge l'interfaccia per disegnare gli slider con la scala giusta, e
 * chiunque debba rileggere uno storico con le codifiche della versione con cui e' stato
 * scritto.
 *
 * Lettura per ogni utente autenticato: e' la descrizione di un formato, non un segreto. Si
 * pubblica come ogni altro documento, con `POST /API/templates`.
 */
@RestController
@RequestMapping("/API/protocol")
class ProtocolController(
        private val protocol: ProtocolService,
        private val registry: TemplateRegistry,
) {

    /** Il dizionario in uso: la versione piu' alta pubblicata. */
    @GetMapping("", "/")
    fun current(): TemplateDTO =
            protocol.current()?.let { toDTO(it) }
                    ?: throw NoSuchElementException("Nessun dizionario di protocollo pubblicato")

    /**
     * Una versione precisa. `1` risolve dentro il MAJOR 1, `1.2.0` legge quella esatta:
     * la prima serve a chi vuole «il protocollo corrente della famiglia 1», la seconda a
     * chi deve riprodurre una decodifica di ieri.
     */
    @GetMapping("/{version}")
    fun byVersion(@PathVariable version: String): TemplateDTO {
        val record =
                if (version.count { it == '.' } == 2) protocol.exact(version)
                else
                        version.substringBefore('.').toIntOrNull()?.let { protocol.byMajor(it) }
                                ?: throw NoSuchElementException("Versione non valida: $version")

        return record?.let { toDTO(it) }
                ?: throw NoSuchElementException("Nessun dizionario di protocollo $version")
    }

    private fun toDTO(record: TemplateRecord) =
            TemplateDTO(
                    kind = record.kind,
                    templateId = record.keyId,
                    resolvedVersion = record.version,
                    status = record.status,
                    modelName = record.modelName,
                    schemaVersion = record.schemaVersion,
                    contentHash = record.contentHash,
                    content = registry.contentAsMap(record),
            )
}
