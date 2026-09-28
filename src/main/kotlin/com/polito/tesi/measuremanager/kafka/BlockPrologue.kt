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

    /**
     * Un blocco solo: un blocco totale, indice 0, cioe' **0x10**.
     *
     * Non e' scritta a mano ma calcolata con [block], perche' scritta a mano era sbagliata:
     * valeva 0x11, cioe' «indice 1 di 1 blocco», che con gli indici da 0 non esiste. Il valore
     * finiva nella sola 0x24, quindi il server mandava 0x10 per una 0x21 che stava in un
     * pacchetto e 0x11 per ogni programmazione breve. Una CU che convalida `indice < totale`
     * avrebbe scartato ogni 0x24 restando in attesa di un blocco mai spedito; una che non
     * convalida avrebbe letto il corpo come se non fosse il primo blocco.
     */
    val SINGLE: Int = block(1, 0)

    /**
     * Compone il byte BLOCK. **Gli indici partono da 0**, il totale da 1: con tre blocchi il
     * primo vale 0x30, l'ultimo 0x32; con un blocco solo vale 0x10.
     *
     * Lo zero non e' una convenzione scelta qui: e' la stessa di tutto il resto della v1.2 —
     * indice del sensore nel modello di MU, `OFFSET`, posizione dei bit nelle bitmap, indice
     * del frammento in `FRAG`. Una sola numerazione significa che la CU non traduce mai.
     *
     * Ne discende una regola di validazione che il firmware puo' applicare in una riga: in un
     * BLOCK valido il **nibble basso e' sempre minore di quello alto**. 0x10 e 0x32 sono
     * validi, 0x00 e 0x11 no.
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
