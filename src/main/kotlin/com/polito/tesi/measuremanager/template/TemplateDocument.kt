package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateStatus
import java.security.MessageDigest
import java.util.TreeMap

/**
 * L'intestazione di un documento del registro: le poche cose che diventano colonna.
 * Tutto il resto resta dentro [content] e non ha bisogno di essere conosciuto dal server.
 */
data class TemplateDocument(
        val kind: TemplateKind,
        val keyId: Int,
        val major: Int,
        val minor: Int,
        val patch: Int,
        val modelName: String?,
        val schemaVersion: String?,
        /** Forma canonica testuale: e' quella su cui si calcola l'impronta. */
        val content: String,
        /** Lo stesso documento come mappa, che e' cio' che finisce nella colonna jsonb. */
        val contentMap: Map<String, Any?>,
        val contentHash: String,
) {
    val version: String
        get() = "$major.$minor.$patch"

    /** Il MAJOR 0 e' sviluppo: si puo' sovrascrivere. Dalla 1 in poi la versione e' immutabile. */
    val initialStatus: TemplateStatus
        get() = if (major == 0) TemplateStatus.DEV else TemplateStatus.PUBLISHED
}

/** Il documento non supera i controlli minimi del registro. */
class TemplateRejected(message: String) : Exception(message)

object TemplateParser {

    private val VERSION = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

    /**
     * Controlli minimi, gli unici che il registro fa oggi: JSON valido, tipo, identificativo
     * e versione presenti e nella forma attesa. Il validatore completo (schema, compatibilita',
     * riferimenti, formule) arriva dopo, chiamato dalla CI sulla pull request.
     */
    fun parse(json: String, mapper: ObjectMapper, kindHint: TemplateKind? = null): TemplateDocument {
        val node: JsonNode =
                try {
                    mapper.readTree(json)
                } catch (e: Exception) {
                    throw TemplateRejected("JSON non valido: ${e.message}")
                }
        if (node !is ObjectNode) throw TemplateRejected("Il documento deve essere un oggetto JSON")

        val kind =
                node.get("kind")?.asText()?.let {
                    TemplateKind.fromString(it)
                            ?: throw TemplateRejected("kind sconosciuto: $it")
                }
                        ?: kindHint
                        ?: throw TemplateRejected("kind mancante e non deducibile dalla cartella")

        val keyId =
                node.get("templateId")?.takeIf { it.isInt }?.asInt()
                        ?: throw TemplateRejected("templateId mancante o non intero")

        val raw =
                node.get("templateVersion")?.asText()
                        ?: throw TemplateRejected("templateVersion mancante")
        val m =
                VERSION.find(raw)
                        ?: throw TemplateRejected("templateVersion non e' nella forma major.minor.patch: $raw")
        val (major, minor, patch) = m.destructured

        @Suppress("UNCHECKED_CAST")
        val sorted = sortKeys(node, mapper) as Map<String, Any?>
        val normalized = mapper.writeValueAsString(sorted)
        return TemplateDocument(
                kind = kind,
                keyId = keyId,
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                modelName = node.get("modelName")?.asText(),
                schemaVersion = node.get("schemaVersion")?.asText(),
                content = normalized,
                contentMap = sorted,
                contentHash = sha256(normalized),
        )
    }

    /**
     * Forma canonica: chiavi ordinate e nessuno spazio superfluo, cosi' due file identici
     * scritti con indentazione diversa hanno la stessa impronta. I commenti `usercomment_*`
     * restano: sono parte del documento.
     */
    private fun sortKeys(node: JsonNode, mapper: ObjectMapper): Any? =
            when {
                node.isObject -> {
                    val sorted = TreeMap<String, Any?>()
                    node.fields().forEach { (k, v) -> sorted[k] = sortKeys(v, mapper) }
                    sorted
                }
                node.isArray -> node.map { sortKeys(it, mapper) }
                else -> mapper.treeToValue(node, Any::class.java)
            }

    private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256")
                    .digest(text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
}
