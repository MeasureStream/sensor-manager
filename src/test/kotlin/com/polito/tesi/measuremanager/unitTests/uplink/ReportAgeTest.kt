package com.polito.tesi.measuremanager.unitTests.uplink

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.FrameStatus
import com.polito.tesi.measuremanager.entities.UplinkFrame
import com.polito.tesi.measuremanager.quality.QualityFlags
import com.polito.tesi.measuremanager.quality.ReportQualityService
import com.polito.tesi.measuremanager.repositories.UplinkFrameRepository
import com.polito.tesi.measuremanager.template.ContentFlag
import com.polito.tesi.measuremanager.template.Fragmentation
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.uplink.ReportAssembler
import com.polito.tesi.measuremanager.uplink.ReportPayloadError
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * Il byte di eta' del report 0x30 (specifica § 8) e cio' che succede quando non c'e'.
 *
 * Il firmware della CU oggi **non** lo manda: il caso da proteggere e' proprio quello. Senza
 * il byte la finestra si chiude all'istante di ricezione e ogni campione porta NO_AGE; con il
 * byte la finestra si sposta indietro dell'eta' dichiarata.
 */
class ReportAgeTest {

    private fun bytes(hex: String) = hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun protocol(age: ContentFlag?) =
            mockk<ProtocolService> {
                every { fragmentation() } returns Fragmentation()
                every { ageFlag() } returns age
            }

    private val ageOnBit3 = ContentFlag(bit = 3, name = ContentFlag.AGE, bytes = 1, fragment = 0)

    @Test
    fun `con il bit di eta' il byte dopo FRAG non e' un dato`() {
        val assembler = ReportAssembler(protocol(ageOnBit3))
        // CONTENT 0x0B = BASE + EXTENDED + eta'; eta' 0x47 = 71, cioe' due ore (± 10 minuti).
        val (header, data) = assembler.parseHeader(bytes("07 0B 80 47 12 34"))

        assertEquals(71, header.ageRaw)
        assertContentEquals(bytes("12 34"), data, "il byte di eta' non deve finire fra le metriche")
        assertEquals(71, assembler.accept(1L, header, data).complete?.ageRaw)
    }

    @Test
    fun `un firmware che non alza il bit non manda il byte, e niente si sposta`() {
        val assembler = ReportAssembler(protocol(ageOnBit3))
        val (header, data) = assembler.parseHeader(bytes("07 03 80 12 34"))

        assertNull(header.ageRaw)
        assertContentEquals(bytes("12 34"), data)
    }

    @Test
    fun `con un dizionario che non dichiara il bit non si legge niente in piu'`() {
        val assembler = ReportAssembler(protocol(null))
        val (header, data) = assembler.parseHeader(bytes("07 0B 80 47 12 34"))

        assertNull(header.ageRaw)
        assertContentEquals(bytes("47 12 34"), data, "e' il controllo sui byte in eccesso a fermarlo, con il suggerimento")
    }

    @Test
    fun `il byte di eta' sta solo nel frammento 0`() {
        val assembler = ReportAssembler(protocol(ageOnBit3))
        val primo = assembler.parseHeader(bytes("07 0B 00 47 AA"))
        val secondo = assembler.parseHeader(bytes("07 0B 81 BB"))

        assertEquals(71, primo.first.ageRaw)
        assertNull(secondo.first.ageRaw)
        assertContentEquals(bytes("BB"), secondo.second)

        assembler.accept(1L, primo.first, primo.second)
        val complete = assembler.accept(1L, secondo.first, secondo.second).complete
        assertEquals(71, complete?.ageRaw)
        assertContentEquals(bytes("AA BB"), complete?.data)
    }

    @Test
    fun `bit alzato ma byte mancante e' un errore leggibile`() {
        val assembler = ReportAssembler(protocol(ageOnBit3))
        assertThrows<ReportPayloadError> { assembler.parseHeader(bytes("07 0B 80")) }
    }

    /* ------------------------------------------------- finestra e flag */

    private val cu = ControlUnit().apply { id = 5; devEui = 99; transmissionInterval = 10 }
    private val receivedAt = OffsetDateTime.parse("2027-01-10T12:00:00Z")

    private fun quality(previous: OffsetDateTime? = null, timeUnsync: Boolean = false): ReportQualityService {
        val protocol =
                mockk<ProtocolService> {
                    every { transmissionPeriodSeconds(10) } returns 600.0
                    every { cuStatusHas(any(), "time_sync_expired") } returns timeUnsync
                }
        val frames =
                mockk<UplinkFrameRepository> {
                    every {
                        findTopByControlUnit_IdAndFportAndStatusInAndReceivedAtBeforeOrderByReceivedAtDesc(any(), any(), any(), any())
                    } returns previous?.let { UplinkFrame(controlUnit = cu, devEui = 99, receivedAt = it, status = FrameStatus.DECODED) }
                }
        return ReportQualityService(protocol, frames, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `senza byte di eta' la finestra finisce alla ricezione e si dichiara NO_AGE`() {
        val timing = quality().timing(cu, receivedAt, ageRaw = null, readWithSnapshot = false)

        assertEquals(receivedAt, timing.windowEnd)
        assertEquals(receivedAt.minusMinutes(10), timing.windowStart, "un periodo di trasmissione prima")
        assertTrue(timing.flags and QualityFlags.NO_AGE != 0)
        assertNull(timing.ageSeconds)
    }

    @Test
    fun `con il byte di eta' la finestra si sposta indietro`() {
        val timing = quality().timing(cu, receivedAt, ageRaw = 71, readWithSnapshot = true)

        assertEquals(receivedAt.minusHours(2), timing.windowEnd)
        assertEquals(600, timing.ageUncertaintySeconds, "il passo della fascia e' l'incertezza sull'istante")
        assertEquals(0, timing.flags and QualityFlags.NO_AGE)
        assertTrue(timing.flags and QualityFlags.SNAPSHOT != 0)
    }

    @Test
    fun `255 vuol dire eta' sconosciuta, cioe' NO_AGE`() {
        val timing = quality().timing(cu, receivedAt, ageRaw = 255, readWithSnapshot = false)
        assertTrue(timing.flags and QualityFlags.NO_AGE != 0)
        assertEquals(receivedAt, timing.windowEnd)
    }

    @Test
    fun `un report perso produce GAP sul successivo`() {
        val onTime = quality(previous = receivedAt.minusMinutes(10)).timing(cu, receivedAt, null, false)
        val afterLoss = quality(previous = receivedAt.minusMinutes(20)).timing(cu, receivedAt, null, false)

        assertEquals(0, onTime.flags and QualityFlags.GAP)
        assertTrue(afterLoss.flags and QualityFlags.GAP != 0)
    }

    @Test
    fun `orologio della CU non sincronizzato`() {
        val timing = quality(timeUnsync = true).timing(cu, receivedAt, null, false)
        assertTrue(timing.flags and QualityFlags.TIME_UNSYNC != 0)
    }
}
