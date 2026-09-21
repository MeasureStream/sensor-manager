package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import com.polito.tesi.measuremanager.entities.TemplateStatus
import com.polito.tesi.measuremanager.template.SensorTemplate

/**
 * Il riferimento a un template, al posto del template intero.
 *
 * Una CU con 48 sensori ripeteva fino a 48 documenti completi in ogni risposta. Qui viaggia
 * solo la coppia `(templateId, major)` piu' i due campi che servono a disegnare la card;
 * il documento si scarica una volta sola da `/API/templates/{kind}/{id}/{major}` e si tiene
 * in cache, perche' una versione pubblicata non cambia mai.
 *
 * [resolvedVersion] e' la versione esatta che ha vinto la risoluzione dentro il MAJOR: va
 * salvata accanto a cio' che ne dipende, ed e' anche l'unica chiave di cache corretta.
 */
data class TemplateRefDTO(
        val kind: TemplateKind,
        val templateId: Int,
        val major: Int,
        val resolvedVersion: String,
        val status: TemplateStatus,
        val modelName: String?,
        /** Tipo in inglese ("temperature", "acceleration"): la UI ci fa i gruppi di categoria. */
        val type: String,
        /** Unita' in notazione D-SI: la card la mostra senza dover aprire il documento. */
        val unit: String?,
)

/** Costruisce il riferimento da cio' che il registro ha risolto. */
fun templateRef(record: TemplateRecord, template: SensorTemplate?) =
        TemplateRefDTO(
                kind = record.kind,
                templateId = record.keyId,
                major = record.major,
                resolvedVersion = record.version,
                status = record.status,
                modelName = record.modelName,
                type = template?.type ?: "",
                unit = template?.unit,
        )
