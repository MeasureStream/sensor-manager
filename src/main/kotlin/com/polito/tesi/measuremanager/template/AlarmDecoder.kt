package com.polito.tesi.measuremanager.template

import java.nio.ByteBuffer
import org.springframework.stereotype.Service

/** Una voce di allarme letta dalla 0xA0, prima di diventare una riga di database. */
data class DecodedAlarm(
        val localId: Int,
        val sensorIndex: Int,
        /** Bit EVT: la condizione e' rientrata invece di essersi attivata. */
        val cleared: Boolean,
        /** La parola completa, con la catena di estensione nei bit alti. */
        val alarmWord: Long,
        val rawValue: Int,
        /** I bit alzati, con il testo risolto e chi li ha definiti. */
        val conditions: List<Map<String, Any?>>,
)

data class DecodedAlarmMessage(val alarmSeq: Int, val entries: List<DecodedAlarm>)

/** Una voce di evento letta dalla 0xA2. */
data class DecodedEvent(
        val source: Int,
        val sensorIndex: Int,
        val code: Int,
        val param: Int,
        val ageRaw: Int,
        val elapsed: ElapsedTime.Elapsed?,
        val description: String,
)

data class DecodedEventMessage(val eventSeq: Int, val entries: List<DecodedEvent>)

/** Il messaggio non e' leggibile: lunghezza incoerente con il numero di voci dichiarato. */
class AlarmPayloadError(message: String) : Exception(message)

/**
 * Lettura degli allarmi 0xA0 e degli eventi 0xA2.
 *
 * Entrambi i formati hanno **voci di lunghezza fissa precedute dal loro numero**, e non e'
 * un caso: con `N_ENTRIES` un payload troncato diventa un errore rilevato invece di una
 * lettura che scorre oltre. Le catene di estensione degli allarmi stanno in coda, dopo tutte
 * le voci, per lo stesso motivo — una catena dentro una voce renderebbe illeggibili tutte le
 * successive se un bit si corrompe.
 */
@Service
class AlarmDecoder {

    /** Allarmi 0xA0: `ALARM_SEQ | N_ENTRIES | voci da 5 byte | catene in coda`. */
    fun decodeAlarms(bytes: ByteArray, templateOf: (Int, Int) -> SensorTemplate?): DecodedAlarmMessage {
        if (bytes.size < 2) throw AlarmPayloadError("Messaggio di allarme piu' corto dell'intestazione")

        val buffer = ByteBuffer.wrap(bytes)
        val alarmSeq = buffer.get().toInt() and 0xFF
        val count = buffer.get().toInt() and 0xFF

        val expected = 2 + 5 * count
        if (bytes.size < expected) {
            throw AlarmPayloadError(
                    "Dichiarate $count voci, servivano $expected byte, ricevuti ${bytes.size}"
            )
        }

        // Prima le voci, tutte: la coda si legge dopo, nello stesso ordine.
        data class Raw(val localId: Int, val sensorByte: Int, val word: Int, val value: Int)

        val raws =
                (0 until count).map {
                    Raw(
                            localId = buffer.get().toInt() and 0xFF,
                            sensorByte = buffer.get().toInt() and 0xFF,
                            word = buffer.get().toInt() and 0xFF,
                            value = buffer.short.toInt() and 0xFFFF,
                    )
                }

        val entries =
                raws.map { raw ->
                    // Il bit 7 del byte base dice «segue una catena», non e' una condizione:
                    // si toglie prima di comporre, altrimenti i bit dell'estensione, che
                    // partono dalla posizione 7, gli finirebbero sopra.
                    var word = (raw.word and 0x7F).toLong()
                    var shift = 7
                    // La catena: un byte per volta, sette bit ciascuno, finche' il bit alto
                    // resta alzato. Se la coda finisce prima, si perde solo la coda.
                    if (raw.word and (1 shl AlarmCatalog.CONTINUATION_BIT) != 0) {
                        while (buffer.remaining() > 0) {
                            val extension = buffer.get().toInt() and 0xFF
                            word = word or ((extension and 0x7F).toLong() shl shift)
                            shift += 7
                            if (extension and 0x80 == 0) break
                        }
                    }

                    val sensorIndex = raw.sensorByte and 0x7F
                    DecodedAlarm(
                            localId = raw.localId,
                            sensorIndex = sensorIndex,
                            cleared = raw.sensorByte and 0x80 != 0,
                            alarmWord = word,
                            rawValue = raw.value,
                            conditions =
                                    resolveConditions(word, templateOf(raw.localId, sensorIndex)),
                    )
                }

        return DecodedAlarmMessage(alarmSeq, entries)
    }

    /**
     * I bit alzati diventano testo. I primi cinque li definisce MeasureStream; il 5 e il 6
     * sono le prime due condizioni dichiarate dal template del sensore, e dalla terza in poi
     * stanno nella catena di estensione.
     */
    private fun resolveConditions(word: Long, template: SensorTemplate?): List<Map<String, Any?>> {
        val declared = templateAlarms(template)
        val conditions = mutableListOf<Map<String, Any?>>()

        for (bit in 0..62) {
            if (word and (1L shl bit) == 0L) continue

            val standard = AlarmCatalog.CONDITIONS[bit]
            if (standard != null) {
                conditions.add(mapOf("bit" to bit, "text" to standard, "source" to "MeasureStream"))
                continue
            }

            // Dal bit 5 in poi sono condizioni dichiarate dal template, nell'ordine in cui il
            // template le elenca: la prima e' il bit 5, e la catena prosegue senza salti.
            val index = bit - 5
            val text = declared.getOrNull(index)
            conditions.add(
                    mapOf(
                            "bit" to bit,
                            "text" to (text ?: "Condizione $index dichiarata dal template"),
                            "source" to "costruttore",
                            "resolved" to (text != null),
                    )
            )
        }
        return conditions
    }

    /** Le condizioni dichiarate dal template del sensore, se il documento le porta. */
    private fun templateAlarms(template: SensorTemplate?): List<String> {
        @Suppress("UNCHECKED_CAST")
        val declared = template?.properties?.get("supportedAlarms") as? List<Map<String, Any?>>
        return declared?.map { (it["description"] ?: it["name"] ?: "").toString() } ?: emptyList()
    }

    /** Eventi 0xA2: `EVENT_SEQ | N_EVENTI | voci da 5 byte`. */
    fun decodeEvents(bytes: ByteArray, constructorCode: (Int, Int) -> String?): DecodedEventMessage {
        if (bytes.size < 2) throw AlarmPayloadError("Messaggio di eventi piu' corto dell'intestazione")

        val buffer = ByteBuffer.wrap(bytes)
        val eventSeq = buffer.get().toInt() and 0xFF
        val count = buffer.get().toInt() and 0xFF

        val expected = 2 + 5 * count
        if (bytes.size < expected) {
            throw AlarmPayloadError(
                    "Dichiarati $count eventi, servivano $expected byte, ricevuti ${bytes.size}"
            )
        }

        val entries =
                (0 until count).map {
                    val source = buffer.get().toInt() and 0xFF
                    val sensorIndex = buffer.get().toInt() and 0xFF
                    val code = buffer.get().toInt() and 0xFF
                    val param = buffer.get().toInt() and 0xFF
                    val ageRaw = buffer.get().toInt() and 0xFF

                    val spec = AlarmCatalog.EVENTS[code]
                    val description =
                            spec?.let {
                                if (it.paramMeaning != null) "${it.description} ($param ${it.paramMeaning})"
                                else it.description
                            }
                                    ?: constructorCode(source, code)
                                    ?: "Codice evento sconosciuto 0x%02X (%s)".format(
                                            code,
                                            AlarmCatalog.ownerOf(code),
                                    )

                    DecodedEvent(
                            source = source,
                            sensorIndex = sensorIndex,
                            code = code,
                            param = param,
                            ageRaw = ageRaw,
                            elapsed = ElapsedTime.decode(ageRaw),
                            description = description,
                    )
                }

        return DecodedEventMessage(eventSeq, entries)
    }
}
