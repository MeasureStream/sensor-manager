package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.TemplateDTO
import com.polito.tesi.measuremanager.dtos.TemplateSummaryDTO
import com.polito.tesi.measuremanager.dtos.toSummaryDTO
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import com.polito.tesi.measuremanager.template.TemplateRegistry
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * Le rotte del registro. La lettura e' per ogni utente autenticato; la pubblicazione e la
 * revoca sono ristrette in SecurityConfig all'account di servizio della CI e agli ADMIN.
 * Vanno aggiunte anche a gateway-iam, che e' l'unico ingresso dall'esterno.
 */
@RestController
@RequestMapping("/API/templates")
class TemplateController(
        private val registry: TemplateRegistry,
) {

    @GetMapping("", "/")
    fun list(
            @RequestParam kind: String?,
            @RequestParam id: Int?,
    ): List<TemplateSummaryDTO> =
            registry.list(kind?.let { parseKind(it) }, id).map { it.toSummaryDTO() }

    /** Risoluzione dentro un MAJOR: e' la forma che usano tutti i riferimenti. */
    @GetMapping("/{kind}/{id}/{major}")
    fun resolve(
            @PathVariable kind: String,
            @PathVariable id: Int,
            @PathVariable major: Int,
    ): TemplateDTO =
            registry.resolve(parseKind(kind), id, major)?.let { toDTO(it) }
                    ?: throw NoSuchElementException("Nessun template $kind $id MAJOR $major")

    /** Versione esatta: immutabile, quindi cacheabile per sempre con ETag = contentHash. */
    @GetMapping("/{kind}/{id}/{major}.{minor}.{patch}")
    fun readExact(
            @PathVariable kind: String,
            @PathVariable id: Int,
            @PathVariable major: Int,
            @PathVariable minor: Int,
            @PathVariable patch: Int,
    ): TemplateDTO =
            registry.read(parseKind(kind), id, "$major.$minor.$patch")?.let { toDTO(it) }
                    ?: throw NoSuchElementException("Template $kind $id $major.$minor.$patch non trovato")

    /** Referto senza pubblicare. */
    @PostMapping("/validate")
    fun validate(@RequestBody json: String, @RequestParam kind: String?): Map<String, Any?> {
        val doc = registry.dryRun(json, kind?.let { parseKind(it) })
        return mapOf(
                "kind" to doc.kind,
                "templateId" to doc.keyId,
                "version" to doc.version,
                "schemaVersion" to doc.schemaVersion,
                "modelName" to doc.modelName,
                "contentHash" to doc.contentHash,
                "accepted" to true,
        )
    }

    @PostMapping("", "/")
    @ResponseStatus(HttpStatus.CREATED)
    fun publish(
            @RequestBody json: String,
            @RequestParam kind: String?,
            authentication: Authentication?,
    ): TemplateSummaryDTO =
            registry.publish(
                            json = json,
                            source = "ci",
                            publishedBy = authentication?.name,
                            kindHint = kind?.let { parseKind(it) },
                    )
                    .toSummaryDTO()

    @PostMapping("/{kind}/{id}/{version}/revoke")
    fun revoke(
            @PathVariable kind: String,
            @PathVariable id: Int,
            @PathVariable version: String,
    ): TemplateSummaryDTO = registry.revoke(parseKind(kind), id, version).toSummaryDTO()

    private fun parseKind(value: String): TemplateKind =
            TemplateKind.fromString(value)
                    ?: throw NoSuchElementException(
                            "kind sconosciuto: $value (sensor, reference, mu, cu, protocol)"
                    )

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
