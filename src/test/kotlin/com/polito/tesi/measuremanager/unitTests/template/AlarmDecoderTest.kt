package com.polito.tesi.measuremanager.unitTests.template

import com.polito.tesi.measuremanager.template.AlarmDecoder
import com.polito.tesi.measuremanager.template.AlarmPayloadError
import com.polito.tesi.measuremanager.template.ElapsedTime
import com.polito.tesi.measuremanager.template.SensorTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * Gli esempi sono quelli dei capitoli 4.10 e 4.11 della documentazione v1.2, byte per byte.
 * Sono l'unica verifica indipendente sia dal decoder sia dal firmware.
 */
class AlarmDecoderTest {

    private val decoder = AlarmDecoder()

    /** Un template che dichiara tre condizioni proprie, come farebbe un costruttore terzo. */
    private val templateConTreCondizioni =
            SensorTemplate(
                    modelName = "TerzaParte",
                    type = "humidity",
                    properties =
                            mapOf(
                                    "supportedAlarms" to
                                            listOf(
                                                    mapOf("description" to "Condensa rilevata"),
                                                    mapOf("description" to "Filtro sporco"),
                                                    mapOf("description" to "Calibrazione scaduta"),
                                            )
                            ),
            )

    private fun bytes(hex: String) =
            hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /* ---------------------------------------------------------- allarmi */

    @Test
    fun `allarme standard - soglia alta`() {
        // 05 01 | 01 00 01 07 D0
        val message = decoder.decodeAlarms(bytes("05 01 01 00 01 07 D0")) { _, _ -> null }

        assertEquals(5, message.alarmSeq)
        assertEquals(1, message.entries.size)

        val entry = message.entries.first()
        assertEquals(1, entry.localId)
        assertEquals(0, entry.sensorIndex)
        assertTrue(!entry.cleared, "l'allarme si e' attivato, non e' rientrato")
        assertEquals(2000, entry.rawValue)
        assertEquals("Soglia alta superata", entry.conditions.single()["text"])
        assertEquals("MeasureStream", entry.conditions.single()["source"])
    }

    @Test
    fun `condizione dichiarata dal template e suo rientro`() {
        // 06 01 | 02 01 20 68 73   poi   07 01 | 02 81 20 6A 1F
        val attivazione =
                decoder.decodeAlarms(bytes("06 01 02 01 20 68 73")) { _, _ ->
                    templateConTreCondizioni
                }
        val rientro =
                decoder.decodeAlarms(bytes("07 01 02 81 20 6A 1F")) { _, _ ->
                    templateConTreCondizioni
                }

        val prima = attivazione.entries.single()
        assertEquals(2, prima.localId)
        assertEquals(1, prima.sensorIndex)
        assertTrue(!prima.cleared)
        // Il bit 5 e' la prima condizione dichiarata dal template, e va risolta in testo.
        assertEquals("Condensa rilevata", prima.conditions.single()["text"])
        assertEquals("costruttore", prima.conditions.single()["source"])

        val seconda = rientro.entries.single()
        assertEquals(1, seconda.sensorIndex, "0x81 e' lo stesso sensore 1 con il bit EVT")
        assertTrue(seconda.cleared, "0x81 ha il bit 7 alzato: e' un rientro")
    }

    @Test
    fun `due voci, la seconda con la catena in coda`() {
        // 08 02 | 01 00 01 07 D0 | 02 01 80 68 73 | 01
        val message =
                decoder.decodeAlarms(bytes("08 02 01 00 01 07 D0 02 01 80 68 73 01")) { _, _ ->
                    templateConTreCondizioni
                }

        assertEquals(2, message.entries.size)
        assertEquals("Soglia alta superata", message.entries[0].conditions.single()["text"])

        // ALARM_WORD 0x80: nessuna condizione nel byte base, catena in coda. Il byte 0x01
        // porta la terza condizione del template.
        val seconda = message.entries[1]
        assertEquals("Calibrazione scaduta", seconda.conditions.single()["text"])
    }

    @Test
    fun `una condizione non dichiarata resta leggibile`() {
        // Stesso messaggio, ma senza template: il bit non sparisce, si dice che non e' risolto.
        val message = decoder.decodeAlarms(bytes("06 01 02 01 20 68 73")) { _, _ -> null }
        val condizione = message.entries.single().conditions.single()
        assertEquals(false, condizione["resolved"])
        assertTrue(condizione["text"].toString().contains("template"))
    }

    @Test
    fun `un payload troncato e' un errore rilevato`() {
        // N_ENTRIES dichiara due voci ma ce n'e' una: senza il controllo si leggerebbe oltre.
        assertThrows<AlarmPayloadError> {
            decoder.decodeAlarms(bytes("08 02 01 00 01 07 D0")) { _, _ -> null }
        }
    }

    /* ----------------------------------------------------------- eventi */

    @Test
    fun `due eventi, uno di sensore e uno di CU`() {
        // 0A 02 | 02 01 40 05 04 | 00 FF 11 03 47
        val message = decoder.decodeEvents(bytes("0A 02 02 01 40 05 04 00 FF 11 03 47")) { _, _ -> null }

        assertEquals(10, message.eventSeq)
        assertEquals(2, message.entries.size)

        val primo = message.entries[0]
        assertEquals(2, primo.source)
        assertEquals(1, primo.sensorIndex)
        assertEquals(0x40, primo.code)
        assertEquals(5, primo.param)
        assertEquals(40L, primo.elapsed?.seconds, "Δt = 4 sono 40 secondi")
        assertTrue(primo.description.contains("Comunicazione col sensore fallita"))
        assertTrue(primo.description.contains("5"), "il parametro va spiegato, non solo mostrato")

        val secondo = message.entries[1]
        assertEquals(0, secondo.source, "0 indica la CU stessa")
        assertEquals(0xFF, secondo.sensorIndex, "0xFF: riguarda il dispositivo, non un canale")
        assertEquals(120 * 60L, secondo.elapsed?.seconds, "Δt = 71 sono 120 minuti")
        assertEquals(10 * 60, secondo.elapsed?.uncertaintySeconds, "fascia da 10 minuti")
    }

    @Test
    fun `un codice sconosciuto non si perde`() {
        val message = decoder.decodeEvents(bytes("01 01 00 FF 7E 00 00")) { _, _ -> null }
        val evento = message.entries.single()
        assertTrue(evento.description.contains("0x7E"))
        assertTrue(evento.description.contains("sensore"), "la fascia dice chi ha assegnato il codice")
    }

    @Test
    fun `codice del costruttore risolto dal modello di MU`() {
        val message =
                decoder.decodeEvents(bytes("01 01 02 00 90 00 00")) { source, code ->
                    if (source == 2 && code == 0x90) "Cartuccia esaurita" else null
                }
        assertEquals("Cartuccia esaurita", message.entries.single().description)
    }

    /* ------------------------------------------------------------ Δt */

    @Test
    fun `la codifica del tempo trascorso segue la tabella`() {
        assertEquals(0L, ElapsedTime.decode(0)?.seconds)
        assertEquals(60L, ElapsedTime.decode(6)?.seconds)
        assertEquals(2 * 60L, ElapsedTime.decode(7)?.seconds)
        assertEquals(60 * 60L, ElapsedTime.decode(65)?.seconds)
        assertEquals(24 * 3600L, ElapsedTime.decode(203)?.seconds)
        assertEquals(25 * 3600L, ElapsedTime.decode(204)?.seconds)
        assertEquals(29 * 86400L, ElapsedTime.decode(254)?.seconds)
        assertEquals(null, ElapsedTime.decode(255), "255: istante sconosciuto")
    }
}
