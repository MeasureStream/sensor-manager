package com.polito.tesi.measuremanager.kafka

/**
 * Il prologo di blocco dei comandi di configurazione 0x21, 0x22, 0x23 e 0x24 (§ 4.13).
 *
 * ```
 * byte 0  BLOCK      4 bit alti = numero di blocchi, 4 bit bassi = indice di questo blocco
 * byte 1  CMD_SEQ    numero del comando: se il server ritrasmette, la CU riconosce il duplicato
 * byte 2  OFFSET     indice globale del primo sensore del blocco (solo 0x21 e 0x22)
 * ```
 *
 * La CU applica la configurazione **solo quando ha ricevuto tutti i blocchi**: una
 * configurazione parziale non entra mai in opera. La 0x0A non ha prologo perche' trasporta
 * valori assoluti e riapplicarla e' innocuo; questi quattro cambiano stato in modi che una
 * doppia applicazione falserebbe — una finestra che riparte, un CFG_VER che si incrementa.
 */
object BlockPrologue {

    /** Tetto sicuro del payload LoRa a DR0/SF12 in EU868. */
    const val MAX_PAYLOAD = 51

    /** Un blocco solo: 4 bit alti = 1 blocco totale, 4 bit bassi = indice 0. */
    const val SINGLE = 0x11

    /**
     * Compone il byte BLOCK. Gli indici partono da 0, il totale da 1: con tre blocchi il
     * primo vale 0x30, l'ultimo 0x32.
     */
    fun block(total: Int, index: Int): Int {
        require(total in 1..15) { "Numero di blocchi fuori dal nibble: $total" }
        require(index in 0 until total) { "Indice di blocco $index fuori da $total blocchi" }
        return ((total and 0x0F) shl 4) or (index and 0x0F)
    }

    /**
     * Spezza uno stream di byte per sensore in blocchi che stiano nel payload.
     *
     * @param headerSize byte del comando prima dello stream, prologo compreso
     * @return per ogni blocco, l'indice globale del primo sensore e i suoi byte
     */
    fun split(stream: List<Int>, headerSize: Int): List<Pair<Int, List<Int>>> {
        val perBlock = (MAX_PAYLOAD - headerSize).coerceAtLeast(1)
        if (stream.isEmpty()) return listOf(0 to emptyList())
        return stream.chunked(perBlock).mapIndexed { index, chunk ->
            (index * perBlock) to chunk
        }
    }
}
