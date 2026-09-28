package com.polito.tesi.measuremanager.unitTests.uplink

import com.polito.tesi.measuremanager.template.Fragmentation
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.uplink.ReportAssembler
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Header e ricomposizione del report 0x30.
 *
 * L'esempio dell'header e' quello del capitolo 4.9: `07 03 80` con le metriche a seguire.
 * Le regole di frammentazione non stanno nel protocollo — FRAG non porta un identificatore di
 * report — quindi sono queste prove a fissarle.
 */
class ReportAssemblerTest {

    private val protocol =
            mockk<ProtocolService> { every { fragmentation() } returns Fragmentation() }

    private val assembler = ReportAssembler(protocol)

    private val devEui = 42L

    private fun bytes(hex: String) =
            hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `header del report, esempio della documentazione`() {
        val (header, data) = assembler.parseHeader(bytes("07 03 80 00 00 27 96"))

        assertEquals(7, header.cfgVersion)
        assertEquals(0x03, header.content, "CONTENT 0x03: BASE piu' EXTENDED")
        assertEquals(0, header.fragmentIndex)
        assertEquals(true, header.last, "FRAG 0x80 e' l'ultimo frammento con indice 0")
        assertContentEquals(bytes("00 00 27 96"), data, "l'header non entra nei dati")
    }

    @Test
    fun `un report non frammentato esce subito`() {
        val (header, data) = assembler.parseHeader(bytes("07 03 80 12 34"))
        val result = assembler.accept(devEui, header, data)

        val complete = assertNotNull(result.complete)
        assertEquals(1, complete.fragments)
        assertContentEquals(bytes("12 34"), complete.data)
        assertNull(result.abandoned)
    }

    @Test
    fun `tre frammenti si ricompongono nell'ordine in cui sono arrivati`() {
        val primo = assembler.parseHeader(bytes("07 03 00 AA"))
        val secondo = assembler.parseHeader(bytes("07 03 01 BB"))
        val terzo = assembler.parseHeader(bytes("07 03 82 CC"))

        assertNull(assembler.accept(devEui, primo.first, primo.second).complete)
        assertNull(assembler.accept(devEui, secondo.first, secondo.second).complete)

        val complete = assertNotNull(assembler.accept(devEui, terzo.first, terzo.second).complete)
        assertEquals(3, complete.fragments)
        assertContentEquals(bytes("AA BB CC"), complete.data)
        assertEquals(7, complete.cfgVersion)
    }

    @Test
    fun `un frammento mancante butta il report, non lo legge a meta'`() {
        val primo = assembler.parseHeader(bytes("07 03 00 AA"))
        // Il frammento 1 si perde per strada: arriva il 2, con il bit di ultimo.
        val terzo = assembler.parseHeader(bytes("07 03 82 CC"))

        assembler.accept(devEui, primo.first, primo.second)
        val result = assembler.accept(devEui, terzo.first, terzo.second)

        assertNull(result.complete, "mezzo report letto sarebbe peggio di nessun report")
        val abandoned = assertNotNull(result.abandoned)
        assertEquals("atteso il frammento 1, arrivato 2", abandoned.reason)
    }

    @Test
    fun `un frammento centrale senza il suo inizio non si accumula`() {
        val secondo = assembler.parseHeader(bytes("07 03 01 BB"))
        val result = assembler.accept(devEui, secondo.first, secondo.second)

        assertNull(result.complete)
        assertNotNull(result.abandoned)
    }

    @Test
    fun `un report nuovo sostituisce quello rimasto aperto`() {
        val primo = assembler.parseHeader(bytes("07 03 00 AA"))
        assembler.accept(devEui, primo.first, primo.second)

        // Il report successivo comincia: indice 0, e questa volta e' anche l'ultimo.
        val nuovo = assembler.parseHeader(bytes("08 03 80 DD"))
        val result = assembler.accept(devEui, nuovo.first, nuovo.second)

        assertEquals(8, assertNotNull(result.complete).cfgVersion)
        val abandoned = assertNotNull(result.abandoned, "il precedente non sparisce in silenzio")
        assertEquals(7, abandoned.cfgVersion)
    }

    @Test
    fun `frammenti con header diverso appartengono a report diversi`() {
        val primo = assembler.parseHeader(bytes("07 03 00 AA"))
        val estraneo = assembler.parseHeader(bytes("08 03 81 BB"))

        assembler.accept(devEui, primo.first, primo.second)
        val result = assembler.accept(devEui, estraneo.first, estraneo.second)

        assertNull(result.complete)
        assertEquals("header diverso fra i frammenti", assertNotNull(result.abandoned).reason)
    }

    @Test
    fun `il CONTENT puo' portare la sua catena e sposta il byte FRAG`() {
        // CONTENT 0x83: bit 7 di continuazione, quindi 0x01 e' un byte esteso e il FRAG
        // arriva dopo. Senza la catena, il decoder leggerebbe 0x01 come FRAG.
        val (header, data) = assembler.parseHeader(bytes("07 83 01 80 12"))

        assertEquals(0x03 or (1 shl 7), header.content)
        assertEquals(true, header.last)
        assertContentEquals(bytes("12"), data)
    }
}
