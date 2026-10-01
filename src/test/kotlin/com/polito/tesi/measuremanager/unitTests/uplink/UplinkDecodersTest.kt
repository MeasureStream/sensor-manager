package com.polito.tesi.measuremanager.unitTests.uplink

import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.uplink.FPort
import com.polito.tesi.measuremanager.uplink.MuStatusDecoder
import com.polito.tesi.measuremanager.uplink.MuStatusPayloadError
import com.polito.tesi.measuremanager.uplink.PollDecoder
import com.polito.tesi.measuremanager.uplink.TopologyDecoder
import com.polito.tesi.measuremanager.uplink.TopologyPayloadError
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * I tre uplink di stato della v1.2, byte per byte secondo le tabelle del capitolo 4.
 *
 * I payload sono costruiti dalla documentazione, non dal firmware: se il firmware cambia
 * formato, questi test devono fallire.
 */
class UplinkDecodersTest {

    private val protocol =
            mockk<ProtocolService> {
                every { batteryMeaning(254) } returns "ac_powered"
                every { batteryMeaning(255) } returns "charging"
                every { batteryMeaning(match { it < 254 }) } returns null
            }

    private val poll = PollDecoder(protocol)
    private val topology = TopologyDecoder()
    private val muStatus = MuStatusDecoder()

    private fun bytes(hex: String) =
            hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /* ------------------------------------------------------------- poll */

    @Test
    fun `poll v1_2 - dieci byte, con il CMD_SEQ applicato`() {
        // 0064 | 07 | 09 | 12 | 58 | 0E | 0021 | 05
        // CU Model 0x0064, CFG_VER 7, CMD_SEQ applicato 9, ProtoVer 0x12, batteria 88%,
        // P_TX 14, Status con i bit 0 e 5, ALARM_SEQ 5.
        val message = poll.decode(bytes("00 64 07 09 12 58 0E 00 21 05"), FPort.POLL)

        assertEquals(0x0064, message.model)
        assertEquals(7, message.cfgVersion)
        assertEquals(9, message.appliedCmdSeq, "il quarto byte dice quale comando e' in opera")
        assertEquals(0x12, message.protocolVer, "ProtoVer 0x12: la CU parla il protocollo v1.2")
        assertEquals(88, message.batteryLevel)
        assertEquals(14, message.transmissionPower)
        assertEquals(0x21L, message.statusWord)
        assertEquals(5, message.alarmSeq)
        assertNull(message.templateVersion, "il campo TEMPLATE_VER non esiste piu'")
    }

    @Test
    fun `poll v1_2 - lo Status porta la sua catena di estensione`() {
        // Status 0x8003: bit 15 alzato, quindi segue un byte esteso. 0x02 porta un solo bit
        // nuovo (il primo della catena, cioe' il 15) e chiude la catena.
        val message = poll.decode(bytes("00 64 07 09 12 58 0E 80 03 02 05"), FPort.POLL)

        // I bit 0 e 1 dal campo base, il bit 16 dall'estensione (0x02 = bit 1 della catena).
        assertEquals(0b11L or (0b10L shl 15), message.statusWord)
        assertEquals(9, message.appliedCmdSeq)
        assertEquals(5, message.alarmSeq, "l'ALARM_SEQ resta in fondo, dopo la catena")
    }

    @Test
    fun `poll precedente - sette byte, con TEMPLATE_VER`() {
        val message = poll.decode(bytes("00 01 03 01 4B 0E 00"), FPort.POLL)

        assertEquals(0x0001, message.model)
        assertEquals(3, message.cfgVersion)
        assertEquals(1, message.templateVersion)
        assertNull(message.protocolVer, "senza ProtoVer la CU non dichiara la v1.2")
        assertNull(message.alarmSeq)
        assertEquals(75, message.batteryLevel)
    }

    @Test
    fun `stato sulla porta precedente - cinque byte`() {
        val message = poll.decode(bytes("00 01 64 0E 00"), FPort.CU_STATUS_LEGACY)

        assertEquals(0x0001, message.model)
        assertEquals(100, message.batteryLevel)
        assertNull(message.cfgVersion, "la 0x0A non porta il CFG_VER")
    }

    @Test
    fun `la batteria a 254 e 255 non e' una percentuale`() {
        val rete = poll.decode(bytes("00 64 07 09 12 FE 0E 00 00 00"), FPort.POLL)
        assertTrue(rete.acPowered)
        assertEquals(100, rete.batteryLevel, "alimentata da rete: la carica non e' un dato")

        val carica = poll.decode(bytes("00 64 07 09 12 FF 0E 00 00 00"), FPort.POLL)
        assertTrue(carica.charging)
    }

    /* -------------------------------------------------------- topologia */

    @Test
    fun `notifica 0x11 - una MU per record, con il MAJOR del modello`() {
        // 00010001 | 02   e   00030007 | 01
        val entries = topology.decode(bytes("00 01 00 01 02 00 03 00 07 01"), FPort.TOPOLOGY)

        assertEquals(2, entries.size)
        assertEquals(0x00010001L, entries[0].extendedId)
        assertEquals(1, entries[0].localId, "i Local ID sono posizionali: la prima MU e' la 1")
        assertEquals(0x0001, entries[0].model, "il modello sta nei 16 bit alti dell'ExtendedID")
        assertEquals(2, entries[0].modelMajor)

        assertEquals(2, entries[1].localId)
        assertEquals(0x0003, entries[1].model)
        assertEquals(1, entries[1].modelMajor)
    }

    @Test
    fun `notifica 0x10 - quattro byte per MU, nessuna versione`() {
        val entries = topology.decode(bytes("00 01 00 01 00 03 00 07"), FPort.TOPOLOGY_LEGACY)

        assertEquals(2, entries.size)
        assertNull(entries[0].modelMajor, "la 0x10 non porta la versione del modello")
    }

    @Test
    fun `una notifica di lunghezza sbagliata e' un errore, non un troncamento`() {
        // Nove byte su record da cinque: l'ultima MU sarebbe letta a meta'.
        assertThrows<TopologyPayloadError> {
            topology.decode(bytes("00 01 00 01 02 00 03 00 07"), FPort.TOPOLOGY)
        }
    }

    /* ------------------------------------------------------- stato MU */

    @Test
    fun `stato MU - la mappa dice a chi appartiene ogni parola`() {
        // STATUS_SEQ 0x0C, MU_MAP 0x05 (bit 0 e bit 2), poi due parole da due byte.
        val message = muStatus.decode(bytes("0C 05 0081 0021"))

        assertEquals(12, message.statusSeq)
        assertEquals(2, message.entries.size)
        assertEquals(1, message.entries[0].localId, "il bit 0 della mappa e' il Local ID 1")
        assertEquals(0x0081L, message.entries[0].statusWord)
        assertEquals(3, message.entries[1].localId, "il bit 2 della mappa e' il Local ID 3")
        assertEquals(0x0021L, message.entries[1].statusWord)
    }

    @Test
    fun `stato MU - la parola puo' portare la catena di estensione`() {
        // 0x8081: bit 15 alzato, segue un byte esteso 0x01 che chiude la catena.
        val message = muStatus.decode(bytes("01 01 80 81 01"))

        val word = message.entries.single().statusWord
        assertEquals(0x81L or (1L shl 15), word)
    }

    @Test
    fun `stato MU - una mappa che promette piu' MU dei byte presenti e' un errore`() {
        assertThrows<MuStatusPayloadError> { muStatus.decode(bytes("01 03 00 81")) }
    }
}
