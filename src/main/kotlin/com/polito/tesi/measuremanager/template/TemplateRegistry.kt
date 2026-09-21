package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import com.polito.tesi.measuremanager.entities.TemplateStatus
import com.polito.tesi.measuremanager.repositories.TemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/** Emesso a ogni pubblicazione o revoca: risveglia cio' che aspettava quel template. */
data class TemplatePublished(
        val kind: TemplateKind,
        val keyId: Int,
        val version: String,
        val status: TemplateStatus,
)

/**
 * Il registro dei template: la fonte a runtime.
 *
 * I file in `/app/templates` non sono piu' letti a ogni avvio come unica verita': sono il
 * seme importato una volta. Da li' in poi si pubblica via API e la cache si aggiorna
 * subito, senza riavviare e senza toccare il database a mano.
 *
 * Risoluzione: `(kind, id, MAJOR)` da' la versione pubblicata piu' alta dentro quel MAJOR;
 * dentro il MAJOR 0 da' la versione di sviluppo corrente. Ogni risposta riporta la versione
 * esatta usata, che va salvata accanto a cio' che ne dipende.
 */
@Service
class TemplateRegistry(
        private val repository: TemplateRepository,
        private val objectMapper: ObjectMapper,
        private val events: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(TemplateRegistry::class.java)

    private data class ExactKey(val kind: TemplateKind, val keyId: Int, val version: String)

    private data class MajorKey(val kind: TemplateKind, val keyId: Int, val major: Int)

    /** Le versioni pubblicate non cambiano mai: questa cache si puo' solo aggiungere. */
    private val byVersion = ConcurrentHashMap<ExactKey, TemplateRecord>()

    /** Indice «ultima dentro il MAJOR»: e' l'unico che si invalida. */
    private val byMajor = ConcurrentHashMap<MajorKey, TemplateRecord>()

    /** Ponte con `sensor.model_name`, finche' il DTO porta il nome invece del riferimento. */
    private val byModelName = ConcurrentHashMap<String, TemplateRecord>()

    // ---------------------------------------------------------------- lettura

    /** La versione esatta, per riprodurre una decodifica storica o firmare un certificato. */
    fun read(kind: TemplateKind, keyId: Int, version: String): TemplateRecord? {
        byVersion[ExactKey(kind, keyId, version)]?.let { return it }
        val parts = version.split(".")
        if (parts.size != 3) return null
        val record =
                repository.findByKindAndKeyIdAndMajorAndMinorAndPatch(
                        kind,
                        keyId,
                        parts[0].toIntOrNull() ?: return null,
                        parts[1].toIntOrNull() ?: return null,
                        parts[2].toIntOrNull() ?: return null,
                )
        return record?.also { cache(it) }
    }

    /** La versione corrente dentro un MAJOR: e' cosi' che i riferimenti vengono risolti. */
    fun resolve(kind: TemplateKind, keyId: Int, major: Int): TemplateRecord? {
        byMajor[MajorKey(kind, keyId, major)]?.let { return it }
        val record =
                repository.findByKindAndKeyIdOrderByMajorDescMinorDescPatchDesc(kind, keyId)
                        .filter { it.major == major && it.status != TemplateStatus.REVOKED }
                        .maxWithOrNull(compareBy({ it.minor }, { it.patch }))
        return record?.also { cache(it) }
    }

    /** Senza MAJOR si prende il piu' alto pubblicato: serve finche' i riferimenti non lo portano. */
    fun resolveLatest(kind: TemplateKind, keyId: Int): TemplateRecord? =
            repository.findByKindAndKeyIdOrderByMajorDescMinorDescPatchDesc(kind, keyId)
                    .firstOrNull { it.status != TemplateStatus.REVOKED }
                    ?.also { cache(it) }

    fun resolveByModelName(modelName: String): TemplateRecord? =
            byModelName[modelName.lowercase()]

    fun list(kind: TemplateKind? = null, keyId: Int? = null): List<TemplateRecord> {
        val all = if (kind != null) repository.findByKind(kind) else repository.findAll()
        return all.filter { keyId == null || it.keyId == keyId }
                .sortedWith(
                        compareBy({ it.kind }, { it.keyId }, { it.major }, { it.minor }, { it.patch })
                )
    }

    // ------------------------------------------------------------ scrittura

    /**
     * Pubblica una versione. Controlli minimi: JSON valido, tipo, identificativo e versione
     * presenti, versione non gia' pubblicata. Dentro il MAJOR 0 la riscrittura e' lecita.
     */
    @Transactional
    fun publish(
            json: String,
            source: String,
            publishedBy: String?,
            kindHint: TemplateKind? = null,
    ): TemplateRecord {
        val doc = TemplateParser.parse(json, objectMapper, kindHint)
        val existing =
                repository.findByKindAndKeyIdAndMajorAndMinorAndPatch(
                        doc.kind, doc.keyId, doc.major, doc.minor, doc.patch
                )

        if (existing != null) {
            if (existing.contentHash == doc.contentHash) return existing // idempotente
            if (existing.status != TemplateStatus.DEV) {
                throw TemplateRejected(
                        "${doc.kind} ${doc.keyId} ${doc.version} e' gia' pubblicata e non si " +
                                "modifica: pubblica la versione successiva"
                )
            }
            existing.content = doc.contentMap
            existing.contentHash = doc.contentHash
            existing.modelName = doc.modelName
            existing.schemaVersion = doc.schemaVersion
            existing.source = source
            existing.publishedBy = publishedBy
            existing.publishedAt = OffsetDateTime.now()
            return repository.save(existing).also { afterWrite(it) }
        }

        val record =
                TemplateRecord(
                        kind = doc.kind,
                        keyId = doc.keyId,
                        major = doc.major,
                        minor = doc.minor,
                        patch = doc.patch,
                        status = doc.initialStatus,
                        modelName = doc.modelName,
                        schemaVersion = doc.schemaVersion,
                        content = doc.contentMap,
                        contentHash = doc.contentHash,
                        source = source,
                        publishedBy = publishedBy,
                )
        return repository.save(record).also { afterWrite(it) }
    }

    /** Referto senza pubblicare: la CI lo usa sulla pull request. */
    fun dryRun(json: String, kindHint: TemplateKind? = null): TemplateDocument {
        val doc = TemplateParser.parse(json, objectMapper, kindHint)
        val existing =
                repository.findByKindAndKeyIdAndMajorAndMinorAndPatch(
                        doc.kind, doc.keyId, doc.major, doc.minor, doc.patch
                )
        if (existing != null &&
                        existing.contentHash != doc.contentHash &&
                        existing.status != TemplateStatus.DEV
        ) {
            throw TemplateRejected(
                    "${doc.kind} ${doc.keyId} ${doc.version} e' gia' pubblicata con un contenuto diverso"
            )
        }
        return doc
    }

    /** Una versione revocata non si assegna piu' e non vince la risoluzione, ma resta leggibile. */
    @Transactional
    fun revoke(kind: TemplateKind, keyId: Int, version: String): TemplateRecord {
        val record =
                read(kind, keyId, version)
                        ?: throw NoSuchElementException("Template $kind $keyId $version non trovato")
        record.status = TemplateStatus.REVOKED
        return repository.save(record).also { afterWrite(it) }
    }

    // -------------------------------------------------------------- interno

    private fun afterWrite(record: TemplateRecord) {
        byMajor.remove(MajorKey(record.kind, record.keyId, record.major))
        byVersion.remove(ExactKey(record.kind, record.keyId, record.version))
        record.modelName?.let { byModelName.remove(it.lowercase()) }
        if (record.status != TemplateStatus.REVOKED) cache(record)
        logger.info(
                "Template {} {} {} {} da {}",
                record.kind,
                record.keyId,
                record.version,
                record.status,
                record.source
        )
        events.publishEvent(
                TemplatePublished(record.kind, record.keyId, record.version, record.status)
        )
    }

    private fun cache(record: TemplateRecord) {
        if (record.status == TemplateStatus.REVOKED) return
        byVersion[ExactKey(record.kind, record.keyId, record.version)] = record
        val majorKey = MajorKey(record.kind, record.keyId, record.major)
        val current = byMajor[majorKey]
        if (current == null ||
                        compareValuesBy(record, current, { it.minor }, { it.patch }) >= 0
        ) {
            byMajor[majorKey] = record
        }
        record.modelName?.let { name ->
            val key = name.lowercase()
            val best = byModelName[key]
            if (best == null ||
                            compareValuesBy(record, best, { it.major }, { it.minor }, { it.patch }) >= 0
            ) {
                byModelName[key] = record
            }
        }
    }

    /** Scalda la cache all'avvio: poche decine di documenti, si tengono tutti in memoria. */
    fun warmUp() {
        byVersion.clear()
        byMajor.clear()
        byModelName.clear()
        repository.findAll().forEach { cache(it) }
        logger.info("Registro template: {} versioni in cache", byVersion.size)
    }

    fun contentAsMap(record: TemplateRecord): Map<String, Any?> = record.content
}
