package com.polito.tesi.measuremanager.uplink

import java.nio.ByteBuffer
import org.springframework.stereotype.Component

/**
 * Una MU dichiarata dalla CU: dove sta e con quale modello e' programmata.
 *
 * [modelMajor] e' la novita' della 0x11. Senza, il server prendeva il MAJOR piu' alto
 * pubblicato del modello e lo applicava a tutte le MU di quel tipo: giusto finche' ne esiste
 * una versione sola, sbagliato in silenzio al primo dispositivo rimasto indietro.
 */
data class MuEntry(
        val extendedId: Long,
        val localId: Int,
        val model: Int,
        val modelMajor: Int? = null,
)

/** Il payload della notifica di topologia non e' leggibile. */
class TopologyPayloadError(message: String) : Exception(message)

/**
 * Notifica delle MU connesse: 0x11, cinque byte per MU.
 *
 * La 0x10 a quattro byte e' stata dismessa il 26 ottobre 2026: non portava il MAJOR del
 * modello, e senza quello il server prendeva la versione piu' alta pubblicata e la applicava
 * a tutte le MU di quel tipo — giusto finche' ne esiste una sola, sbagliato in silenzio al
 * primo dispositivo rimasto indietro.
 *
 * I Local ID non viaggiano: sono posizionali, la prima MU e' la 1. L'`ExtendedID` porta il
 * modello nei suoi 16 bit alti, ed e' l'unico posto da cui il server lo ricava.
 */
@Component
class TopologyDecoder {

    private companion object {
        /** ExtendedID su quattro byte piu' il MAJOR del modello. */
        const val RECORD_SIZE = 5
    }

    fun decode(bytes: ByteArray, fport: Int): List<MuEntry> {
        val recordSize = RECORD_SIZE

        // Nessuna MU connessa: un byte solo, 0x00. Non e' un payload vuoto perche' LoRaWAN
        // include la FPort solo quando c'e' un payload, e senza FPort il server non saprebbe
        // nemmeno che messaggio e'. Un byte non si confonde con una lista: ogni MU ne occupa
        // cinque. E' la risposta che arriva quando l'ultima MU viene staccata, quindi
        // scartarla significherebbe non accorgersi mai di un distacco.
        if (bytes.size == 1) {
            if (bytes[0].toInt() != 0) {
                throw TopologyPayloadError(
                        "${FPort.label(fport)}: un byte solo vale 0x00 (nessuna MU), ricevuto 0x%02X"
                                .format(bytes[0].toInt() and 0xFF)
                )
            }
            return emptyList()
        }

        if (bytes.isEmpty() || bytes.size % recordSize != 0) {
            throw TopologyPayloadError(
                    "${FPort.label(fport)}: ${bytes.size} byte non sono un multiplo di $recordSize"
            )
        }

        val buffer = ByteBuffer.wrap(bytes)
        val entries = mutableListOf<MuEntry>()
        var localId = 1

        while (buffer.remaining() >= recordSize) {
            // Senza segno: l'ExtendedID e' un identificativo, non un numero con cui contare.
            val extendedId = buffer.int.toLong() and 0xFFFFFFFFL
            val major = buffer.get().toInt() and 0xFF
            entries.add(
                    MuEntry(
                            extendedId = extendedId,
                            localId = localId++,
                            model = ((extendedId shr 16) and 0xFFFF).toInt(),
                            modelMajor = major,
                    )
            )
        }
        return entries
    }
}
