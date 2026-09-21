package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import com.polito.tesi.measuremanager.entities.TemplateStatus
import java.time.OffsetDateTime

/** Una riga dell'elenco: l'intestazione, senza il documento. */
data class TemplateSummaryDTO(
        val kind: TemplateKind,
        val templateId: Int,
        val version: String,
        val major: Int,
        val status: TemplateStatus,
        val modelName: String?,
        val schemaVersion: String?,
        val contentHash: String,
        val source: String,
        val publishedAt: OffsetDateTime,
)

/**
 * Il documento risolto. [resolvedVersion] e' la versione esatta che ha vinto la risoluzione
 * dentro il MAJOR chiesto: va salvata accanto a cio' che ne dipende.
 */
data class TemplateDTO(
        val kind: TemplateKind,
        val templateId: Int,
        val resolvedVersion: String,
        val status: TemplateStatus,
        val modelName: String?,
        val schemaVersion: String?,
        val contentHash: String,
        val content: Map<String, Any?>,
)

fun TemplateRecord.toSummaryDTO() =
        TemplateSummaryDTO(
                kind = kind,
                templateId = keyId,
                version = version,
                major = major,
                status = status,
                modelName = modelName,
                schemaVersion = schemaVersion,
                contentHash = contentHash,
                source = source,
                publishedAt = publishedAt,
        )
