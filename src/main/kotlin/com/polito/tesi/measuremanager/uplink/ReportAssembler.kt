package com.polito.tesi.measuremanager.uplink

import com.polito.tesi.measuremanager.template.ProtocolService
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** L'header del report 0x30, gia' separato dai dati. */
data class ReportHeader(
        val cfgVersion: Int,
        val content: Int?,
        val fragmentIndex: Int,
        val last: Boolean,
)

/** Un report intero, pronto da leggere. */
data class AssembledReport(
        val cfgVersion: Int,
        val content: Int?,
        val data: ByteArray,
        val fragments: Int,
)

/** Un report lasciato a meta': i byte che c'erano e il motivo per cui non si completera'. */
data class AbandonedReport(
        val devEui: Long,
        val cfgVersion: Int,
        val data: ByteArray,
        val fragments: Int,
        val reason: String,
)

/**
 * Esito di un frammento: al piu' un report completo, al piu' uno abbandonato. Possono
 * comparire insieme, perche' un frammento con indice 0 chiude il precedente e ne apre un
 * altro nello stesso istante.
 */
data class AssemblyResult(
        val complete: AssembledReport? = null,
        val abandoned: AbandonedReport? = null,
)

/** Il payload del report non e' leggibile nemmeno nell'header. */
class ReportPayloadError(message: String) : Exception(message)

/**
 * Ricompone i report frammentati (byte FRAG della 0x30).
 *
 * FRAG porta l'indice del frammento e il bit di ultimo, ma **non un identificatore di
 * report**: la documentazione lo elenca fra le questioni aperte, perche' se si perde un
 * frammento centrale il primo frammento del report successivo finisce nello stesso accumulo.
 * Servono quindi due regole scritte, ed eccole: un frammento con indice 0 ricomincia sempre
 * da capo, e un accumulo piu' vecchio del timeout si considera perso. Quel che si butta non
 * sparisce: diventa una riga di uplink_frame con il motivo.
 *
 * L'accumulo sta in memoria: un riavvio lo perde. E' la scelta giusta finche' i frammenti
 * durano secondi — persisterli significherebbe una tabella e una migrazione per uno stato
 * che vive meno di un ciclo di trasmissione.
 */
@Service
class ReportAssembler(
        private val protocol: ProtocolService,
) {
    private val log = LoggerFactory.getLogger(ReportAssembler::class.java)

    /** Oltre questa dimensione l'accumulo si interrompe: nessun report vero ci arriva. */
    private val maxBytes = 2048

    private data class Open(
            val cfgVersion: Int,
            val content: Int?,
            val chunks: MutableList<ByteArray>,
            var nextIndex: Int,
            val startedAt: Instant,
    ) {
        fun size() = chunks.sumOf { it.size }

        fun data() = chunks.reduceOrNull { a, b -> a + b } ?: ByteArray(0)
    }

    private val open = ConcurrentHashMap<Long, Open>()

    /** Header del report v1.2: CFG_VER, CONTENT con la sua catena, FRAG. */
    fun parseHeader(bytes: ByteArray): Pair<ReportHeader, ByteArray> {
        if (bytes.size < 3) {
            throw ReportPayloadError("report 0x30: ${bytes.size} byte, ne servono almeno 3")
        }

        val buffer = ByteBuffer.wrap(bytes)
        val cfgVersion = buffer.get().toInt() and 0xFF
        val content = Continuation.read8(buffer).toInt()
        if (buffer.remaining() < 1) throw ReportPayloadError("report 0x30: manca il byte FRAG")
        val frag = buffer.get().toInt() and 0xFF

        val rules = protocol.fragmentation()
        val header =
                ReportHeader(
                        cfgVersion = cfgVersion,
                        content = content,
                        fragmentIndex = frag and rules.indexMask,
                        last = (frag shr rules.lastBit) and 1 == 1,
                )

        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return header to data
    }

    /**
     * Aggiunge un frammento. Il caso normale — un report che sta in un pacchetto — attraversa
     * questa funzione senza toccare la mappa: indice 0 e bit di ultimo alzato escono subito.
     */
    fun accept(devEui: Long, header: ReportHeader, data: ByteArray): AssemblyResult {
        val rules = protocol.fragmentation()

        if (header.fragmentIndex == 0 && header.last) {
            val abandoned =
                    open.remove(devEui)?.let { abandon(devEui, it, "sostituito da un report nuovo") }
            return AssemblyResult(
                    complete = AssembledReport(header.cfgVersion, header.content, data, 1),
                    abandoned = abandoned,
            )
        }

        var abandoned: AbandonedReport? = null
        var current = open[devEui]

        // Un accumulo troppo vecchio non si completera' piu': i frammenti che mancavano sono
        // andati persi, e tenerlo aperto farebbe finire in mezzo quelli del report successivo.
        if (current != null && current.startedAt.plusSeconds(rules.timeoutSeconds).isBefore(Instant.now())) {
            abandoned = abandon(devEui, current, "frammenti incompleti oltre ${rules.timeoutSeconds} s")
            open.remove(devEui)
            current = null
        }

        if (header.fragmentIndex == 0) {
            if (current != null && rules.resetOnIndexZero) {
                abandoned = abandon(devEui, current, "sostituito da un report nuovo")
            }
            val fresh =
                    Open(
                            cfgVersion = header.cfgVersion,
                            content = header.content,
                            chunks = mutableListOf(),
                            nextIndex = 0,
                            startedAt = Instant.now(),
                    )
            open[devEui] = fresh
            current = fresh
        }

        if (current == null) {
            // Un frammento centrale senza il suo inizio: da solo non si legge, perche' i byte
            // non cominciano al confine di una metrica.
            return AssemblyResult(
                    abandoned =
                            AbandonedReport(
                                    devEui = devEui,
                                    cfgVersion = header.cfgVersion,
                                    data = data,
                                    fragments = 1,
                                    reason = "frammento ${header.fragmentIndex} senza il frammento 0",
                            )
            )
        }

        // L'header si ripete su ogni frammento: se cambia, i pezzi appartengono a due report.
        if (current.cfgVersion != header.cfgVersion || current.content != header.content) {
            val broken = abandon(devEui, current, "header diverso fra i frammenti")
            open.remove(devEui)
            return AssemblyResult(abandoned = broken)
        }

        if (header.fragmentIndex != current.nextIndex) {
            val broken =
                    abandon(
                            devEui,
                            current,
                            "atteso il frammento ${current.nextIndex}, arrivato ${header.fragmentIndex}",
                    )
            open.remove(devEui)
            return AssemblyResult(abandoned = broken)
        }

        current.chunks.add(data)
        current.nextIndex += 1

        if (current.size() > maxBytes) {
            val broken = abandon(devEui, current, "report oltre $maxBytes byte")
            open.remove(devEui)
            return AssemblyResult(abandoned = broken)
        }

        if (!header.last) {
            log.debug("DevEUI={}: frammento {} accumulato", devEui, header.fragmentIndex)
            return AssemblyResult(abandoned = abandoned)
        }

        open.remove(devEui)
        return AssemblyResult(
                complete =
                        AssembledReport(
                                cfgVersion = current.cfgVersion,
                                content = current.content,
                                data = current.data(),
                                fragments = current.chunks.size,
                        ),
                abandoned = abandoned,
        )
    }

    /** Gli accumuli scaduti, per chi li deve registrare. Li rimuove. */
    fun expire(): List<AbandonedReport> {
        val limit = protocol.fragmentation().timeoutSeconds
        val now = Instant.now()
        return open.entries
                .filter { it.value.startedAt.plusSeconds(limit).isBefore(now) }
                .map { (devEui, report) ->
                    open.remove(devEui)
                    abandon(devEui, report, "frammenti incompleti oltre $limit s")
                }
    }

    private fun abandon(devEui: Long, report: Open, reason: String): AbandonedReport {
        log.warn(
                "DevEUI={}: report incompleto scartato dopo {} frammenti ({})",
                devEui,
                report.chunks.size,
                reason,
        )
        return AbandonedReport(devEui, report.cfgVersion, report.data(), report.chunks.size, reason)
    }
}
