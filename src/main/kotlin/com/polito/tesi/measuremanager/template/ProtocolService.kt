package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Il dizionario di protocollo, letto dal registro.
 *
 * Le codifiche non lineari dei periodi erano scritte a mano in tre posti: qui, negli slider
 * dell'interfaccia e nella conversione indice → etichetta. Ora la fonte e' il documento
 * `kind: protocol`, e il server non porta piu' nessuna tabella: se il dizionario manca,
 * [transmissionPeriodMinutes] restituisce null e chi lo chiama ricade sul suo valore di
 * sicurezza, invece di usare una copia che potrebbe essere vecchia.
 */
@Service
class ProtocolService(
        private val registry: TemplateRegistry,
        private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(ProtocolService::class.java)

    /** C'e' una sola famiglia di protocollo: la versione la identifica il MAJOR. */
    private val familyId = 1

    /** Il dizionario in uso: la versione piu' alta pubblicata. */
    fun current(): TemplateRecord? = registry.resolveLatest(TemplateKind.PROTOCOL, familyId)

    /** Il dizionario di un MAJOR preciso, per rileggere uno storico con le sue codifiche. */
    fun byMajor(major: Int): TemplateRecord? =
            registry.resolve(TemplateKind.PROTOCOL, familyId, major)

    fun exact(version: String): TemplateRecord? =
            registry.read(TemplateKind.PROTOCOL, familyId, version)

    /** La forma tipizzata: solo le tabelle che il server usa. */
    fun dictionary(record: TemplateRecord? = current()): ProtocolDictionary? =
            record?.let { objectMapper.convertValue(it.content, ProtocolDictionary::class.java) }

    /**
     * Periodo di trasmissione in minuti per l'indice dichiarato dalla CU, o null se il
     * dizionario non c'e' o l'indice e' fuori scala. Serve a decidere se una CU e' online.
     */
    fun transmissionPeriodMinutes(index: Int): Long? {
        val scale = dictionary()?.transmission
        if (scale == null) {
            logger.debug("Dizionario di protocollo assente: periodo di trasmissione non risolto")
            return null
        }
        val seconds = scale.seconds(index) ?: return null
        return (seconds / 60).toLong().takeIf { it > 0 }
    }
}
