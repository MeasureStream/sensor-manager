package com.polito.tesi.measuremanager.unitTests.kafka

import com.polito.tesi.measuremanager.kafka.BlockPrologue
import com.polito.tesi.measuremanager.kafka.LorawanPayloadEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * Il byte BLOCK del prologo (§ 4.13).
 *
 * Questo test esiste perche' la sua assenza ha lasciato passare un errore di uno: la costante
 * del blocco singolo valeva 0x11, cioe' «indice 1 di 1 blocco», e finiva nella sola 0x24.
 * L'indice del blocco non nomina il blocco, decide **come si legge il corpo** — solo il blocco
 * 0 porta `CU Trans. T` — quindi uno sfasamento di uno produce una configurazione sbagliata e
 * silenziosa, oppure un comando che non si completa mai.
 */
class BlockPrologueTest {

    private val encoder = LorawanPayloadEncoder()

    @Test
    fun `gli indici dei blocchi partono da 0`() {
        assertEquals(0x10, BlockPrologue.block(1, 0), "un blocco solo: totale 1, indice 0")
        assertEquals(0x30, BlockPrologue.block(3, 0))
        assertEquals(0x31, BlockPrologue.block(3, 1))
        assertEquals(0x32, BlockPrologue.block(3, 2), "l'ultimo di tre e' l'indice 2, non il 3")
    }

    @Test
    fun `il blocco singolo nasce dalla regola, non da un letterale`() {
        assertEquals(BlockPrologue.block(1, 0), BlockPrologue.SINGLE)
        assertEquals(0x10, BlockPrologue.SINGLE)
    }

    @Test
    fun `un indice fuori dal totale non si compone`() {
        // 0x11 era esattamente questo: indice 1 di un blocco solo.
        assertThrows<IllegalArgumentException> { BlockPrologue.block(1, 1) }
        assertThrows<IllegalArgumentException> { BlockPrologue.block(3, 3) }
        assertThrows<IllegalArgumentException> { BlockPrologue.block(0, 0) }
        assertThrows<IllegalArgumentException> { BlockPrologue.block(16, 0) }
    }

    @Test
    fun `in un BLOCK valido il nibble basso e' minore di quello alto`() {
        // E' la regola con cui il firmware riconosce un prologo malformato, in una riga.
        for (total in 1..15) {
            for (index in 0 until total) {
                val byte = BlockPrologue.block(total, index)
                assertTrue(
                        (byte and 0x0F) < ((byte shr 4) and 0x0F),
                        "BLOCK 0x%02X con %d blocchi, indice %d".format(byte, total, index),
                )
            }
        }
    }

    @Test
    fun `la programmazione breve 0x24 comincia con 0x10`() {
        val payloads =
                encoder.encodeShortSchedule(
                        cmdSeq = 7,
                        transmissionIndex = 4,
                        startHours = 0,
                        stopHours = 0,
                )

        val payload = payloads.single()
        assertEquals(0x24, payload.fPort)
        assertEquals(
                0x10,
                payload.bytes[0].toInt() and 0xFF,
                "un pacchetto solo: un blocco totale, indice 0",
        )
        assertEquals(7, payload.bytes[1].toInt() and 0xFF, "il CMD_SEQ resta il secondo byte")
        assertEquals(7, payload.bytes.size, "sette byte, indipendenti dal numero di sensori")
    }
}
