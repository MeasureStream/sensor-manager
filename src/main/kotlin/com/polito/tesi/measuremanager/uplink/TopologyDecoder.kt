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
 * Notifica delle MU connesse: 0x11 versioned, 0x10 nel formato deprecato.
 *
 * I Local ID non viaggiano: sono posizionali, la prima MU e' la 1. L'`ExtendedID` porta il
 * modello nei suoi 16 bit alti, ed e' l'unico posto da cui il server lo ricava.
 */
@Component
class TopologyDecoder {

    fun decode(bytes: ByteArray, fport: Int): List<MuEntry> {
        val recordSize = if (fport == FPort.TOPOLOGY) 5 else 4
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
            val major = if (recordSize == 5) buffer.get().toInt() and 0xFF else null
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
