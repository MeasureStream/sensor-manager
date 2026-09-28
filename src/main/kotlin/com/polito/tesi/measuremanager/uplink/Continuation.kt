package com.polito.tesi.measuremanager.uplink

import java.nio.ByteBuffer

/**
 * La regola del bit di continuazione (§ 4.4 della documentazione v1.2).
 *
 * Un campo di bitmap dichiara con il proprio bit alto che dopo di se' segue una catena di
 * byte: ognuno porta sette bit nuovi e, nel bit 7, la promessa che ne segue un altro. E' il
 * modo in cui il protocollo cresce senza cambiare formato — e senza di esso un bit aggiunto
 * domani sposterebbe tutti i campi che vengono dopo.
 *
 * I bit della catena si compongono **dopo** quelli utili del campo base: il bit di
 * continuazione non e' un dato e va tolto prima, altrimenti il primo bit esteso finirebbe
 * sopra di lui. E' lo stesso errore che la parola di allarme aveva prima della v1.2.
 */
object Continuation {

    /** Campo a 16 bit (bit 15 = continuazione): Status della CU, parola di stato della MU. */
    fun read16(buffer: ByteBuffer): Long = read(buffer, width = 16)

    /** Campo a 8 bit (bit 7 = continuazione): CONTENT, Setting1, Command1. */
    fun read8(buffer: ByteBuffer): Long = read(buffer, width = 8)

    private fun read(buffer: ByteBuffer, width: Int): Long {
        val continuationBit = width - 1
        val base =
                if (width == 16) (buffer.short.toInt() and 0xFFFF)
                else (buffer.get().toInt() and 0xFF)

        var word = (base and ((1 shl continuationBit) - 1)).toLong()
        if ((base shr continuationBit) and 1 == 0) return word

        var shift = continuationBit
        while (buffer.remaining() >= 1) {
            val extension = buffer.get().toInt() and 0xFF
            word = word or ((extension and 0x7F).toLong() shl shift)
            shift += 7
            if ((extension shr 7) and 1 == 0) break
        }
        return word
    }
}
