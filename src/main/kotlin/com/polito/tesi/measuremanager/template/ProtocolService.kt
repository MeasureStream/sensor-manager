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
 * ogni lettura restituisce null o l'elenco vuoto e chi la usa ricade sul proprio valore di
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

    /**
     * L'ultima conversione, con la versione da cui viene. Il dizionario si legge a ogni
     * report: convertirlo ogni volta significherebbe rifare lo stesso lavoro migliaia di
     * volte al giorno. La chiave e' la versione, quindi una pubblicazione lo invalida da sola.
     */
    @Volatile private var cached: Pair<String, ProtocolDictionary>? = null

    /** Il dizionario in uso: la versione piu' alta pubblicata. */
    fun current(): TemplateRecord? = registry.resolveLatest(TemplateKind.PROTOCOL, familyId)

    /** Il dizionario di un MAJOR preciso, per rileggere uno storico con le sue codifiche. */
    fun byMajor(major: Int): TemplateRecord? =
            registry.resolve(TemplateKind.PROTOCOL, familyId, major)

    fun exact(version: String): TemplateRecord? =
            registry.read(TemplateKind.PROTOCOL, familyId, version)

    /** La forma tipizzata: solo le tabelle che il server usa. */
    fun dictionary(record: TemplateRecord? = current()): ProtocolDictionary? {
        val source = record ?: return null
        cached?.let { (version, dictionary) -> if (version == source.version) return dictionary }
        val converted = objectMapper.convertValue(source.content, ProtocolDictionary::class.java)
        cached = source.version to converted
        return converted
    }

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

    /* ------------------------------------------------------- report 0x30 */

    /** L'ordine canonico delle metriche. Vuoto se il dizionario non lo dichiara. */
    fun metrics(): List<MetricEntry> = dictionary()?.metrics.orEmpty()

    /**
     * Le classi di metriche presenti in un report, lette dal byte CONTENT.
     *
     * Null significa «non filtrare»: e' cio' che vale per i report che il CONTENT non ce
     * l'hanno (la vecchia FPort 0x21) e per un dizionario che non dichiara la tabella. Meglio
     * leggere tutte le metriche configurate che inventare un filtro.
     */
    fun classesIn(content: Int?): Set<String>? {
        if (content == null) return null
        val table = dictionary()?.contentClasses.orEmpty()
        if (table.isEmpty()) return null
        return table.filter { (content shr it.bit) and 1 == 1 }.map { it.metricClass }.toSet()
    }

    fun fragmentation(): Fragmentation = dictionary()?.fragmentation ?: Fragmentation()

    /* ------------------------------------------------------- sentinelle */

    /**
     * Cosa significa un valore di batteria: `ac_powered`, `charging`, o null se e' una
     * percentuale vera. Senza dizionario restano i due valori della documentazione, perche'
     * l'alternativa sarebbe salvare 254 come se fosse una carica del 254%.
     */
    fun batteryMeaning(raw: Int): String? {
        val table = dictionary()?.sentinels?.get("battery")
        if (table != null && table.isNotEmpty()) {
            return table.firstOrNull { it.value.toInt() == raw }?.meaning
        }
        return when (raw) {
            254 -> "ac_powered"
            255 -> "charging"
            else -> null
        }
    }

    /* ------------------------------------------------- parole di stato */

    /** I bit alzati nella parola di stato della CU, con il loro significato. */
    fun statusFlags(word: Int?): List<StatusBit> = flags(word, dictionary()?.statusBits.orEmpty())

    /** I bit alzati nella parola di stato di una MU. */
    fun muStatusFlags(word: Int?): List<StatusBit> =
            flags(word, dictionary()?.muStatusBits.orEmpty())

    /**
     * Un bit alzato che il dizionario non descrive non sparisce: compare come «non
     * documentato». Il bit 15 e' la continuazione e non e' uno stato, quindi resta fuori.
     */
    private fun flags(word: Int?, table: List<StatusBit>): List<StatusBit> {
        if (word == null || word == 0) return emptyList()
        val byBit = table.associateBy { it.bit }
        return (0..30).filter { (word shr it) and 1 == 1 }.map { bit ->
            byBit[bit]
                    ?: StatusBit(
                            bit = bit,
                            meaning = "bit_$bit",
                            description = "Bit $bit alzato, non descritto dal dizionario",
                    )
        }
    }
}
