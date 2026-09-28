package com.polito.tesi.measuremanager.uplink

import java.nio.ByteBuffer
import org.springframework.stereotype.Component

/** Lo stato di una MU: l'indirizzo e i sedici bit, catena di estensione compresa. */
data class MuStatusEntry(val localId: Int, val statusWord: Long)

/** Un comando 0x12 letto: la sequenza e lo stato di ogni MU dichiarata nella mappa. */
data class MuStatusMessage(val statusSeq: Int, val entries: List<MuStatusEntry>)

/** Il payload dello stato MU non e' leggibile. */
class MuStatusPayloadError(message: String) : Exception(message)

/**
 * Stato delle MU (0x12): STATUS_SEQ, MU_MAP e una parola di stato per ogni MU presente.
 *
 * MU_MAP e' una mappa di bit, non un contatore: il bit *i* indica la MU con Local ID *i+1*.
 * Cosi' una MU staccata non sposta le altre, e lo stato di ciascuna resta al proprio posto
 * anche quando la topologia cambia fra un messaggio e l'altro.
 */
@Component
class MuStatusDecoder {

    fun decode(bytes: ByteArray): MuStatusMessage {
        if (bytes.size < 2) {
            throw MuStatusPayloadError("stato MU: ${bytes.size} byte, ne servono almeno 2")
        }

        val buffer = ByteBuffer.wrap(bytes)
        val statusSeq = buffer.get().toInt() and 0xFF
        val map = buffer.get().toInt() and 0xFF

        val entries = mutableListOf<MuStatusEntry>()
        for (bit in 0..7) {
            if ((map shr bit) and 1 == 0) continue
            if (buffer.remaining() < 2) {
                throw MuStatusPayloadError(
                        "stato MU: MU_MAP dichiara la MU ${bit + 1} ma i byte sono finiti"
                )
            }
            entries.add(MuStatusEntry(localId = bit + 1, statusWord = Continuation.read16(buffer)))
        }
        return MuStatusMessage(statusSeq, entries)
    }
}
