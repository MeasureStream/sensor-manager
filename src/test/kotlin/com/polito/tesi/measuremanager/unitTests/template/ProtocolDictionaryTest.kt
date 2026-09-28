package com.polito.tesi.measuremanager.unitTests.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import com.polito.tesi.measuremanager.entities.TemplateStatus
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.ReportDecoder
import com.polito.tesi.measuremanager.template.SensorConversion
import com.polito.tesi.measuremanager.template.TemplateRegistry
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le tabelle del dizionario di protocollo e cosa ci fa il decoder.
 *
 * Il documento e' quello vero (versione 1.2.1 di `protocol/protocol_v1_2.json`), ridotto alle
 * tabelle in prova. Verifica la catena intera: JSON → vista tipizzata → metriche attese.
 */
class ProtocolDictionaryTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    private val document =
            """
            {
              "templateVersion": "1.2.1",
              "metrics": [
                { "id": 1,  "bit": 0,  "name": "mean",     "bytes": 2, "class": "BASE" },
                { "id": 2,  "bit": 1,  "name": "variance", "bytes": 2, "class": "BASE" },
                { "id": 3,  "bit": 2,  "name": "max",      "bytes": 2, "class": "EXTENDED" },
                { "id": 4,  "bit": 3,  "name": "min",      "bytes": 2, "class": "EXTENDED" },
                { "id": 5,  "bit": 4,  "name": "integral", "bytes": 4, "class": "EXTENDED" },
                { "id": 8,  "bit": 7,  "name": "median",   "bytes": 2, "class": "RARE" }
              ],
              "contentClasses": [
                { "bit": 0, "class": "BASE" },
                { "bit": 1, "class": "EXTENDED" },
                { "bit": 2, "class": "RARE" }
              ],
              "statusBits": [
                { "bit": 1, "kind": "state", "meaning": "config_pending", "description": "Configurazione in attesa o blocchi mancanti" },
                { "bit": 8, "kind": "event", "meaning": "reset", "description": "CU resettata dall'ultimo contatto" }
              ],
              "muStatusBits": [
                { "bit": 7, "origin": "cu", "meaning": "online", "description": "MU online" }
              ],
              "sentinels": {
                "battery": [
                  { "value": 254, "meaning": "ac_powered" },
                  { "value": 255, "meaning": "charging" }
                ]
              }
            }
            """.trimIndent()

    @Suppress("UNCHECKED_CAST")
    private val content = mapper.readValue(document, Map::class.java) as Map<String, Any?>

    private val registry =
            mockk<TemplateRegistry> {
                every { resolveLatest(TemplateKind.PROTOCOL, 1) } returns
                        TemplateRecord(
                                kind = TemplateKind.PROTOCOL,
                                keyId = 1,
                                major = 1,
                                minor = 2,
                                patch = 1,
                                status = TemplateStatus.PUBLISHED,
                                content = content,
                                contentHash = "prova",
                        )
            }

    private val protocol = ProtocolService(registry, mapper)
    private val decoder = ReportDecoder(SensorConversion(), protocol)

    @Test
    fun `la tabella delle metriche si legge, class compreso`() {
        val metrics = protocol.metrics()

        assertEquals(6, metrics.size)
        assertEquals("mean", metrics.first().name)
        assertEquals("BASE", metrics.first().metricClass, "la chiave nel JSON si chiama class")
        assertEquals(4, metrics.first { it.name == "integral" }.bytes)
    }

    @Test
    fun `CONTENT dice quali classi viaggiano`() {
        assertEquals(setOf("BASE"), protocol.classesIn(0b001))
        assertEquals(setOf("BASE", "EXTENDED"), protocol.classesIn(0b011))
        assertNull(protocol.classesIn(null), "senza CONTENT non si filtra per classe")
    }

    @Test
    fun `le metriche attese sono quelle configurate, filtrate per classe`() {
        // Un sensore configurato con max e min: sono EXTENDED.
        assertEquals(listOf("max", "min"), decoder.expectedMetrics("max-min", 0b011))
        assertEquals(
                emptyList<String>(),
                decoder.expectedMetrics("max-min", 0b001),
                "un report di sole BASE non porta max e min: nessun byte da leggere",
        )
        assertEquals(listOf("mean", "variance"), decoder.expectedMetrics("average-std", 0b011))
    }

    @Test
    fun `senza CONTENT si leggono tutte le metriche configurate`() {
        assertEquals(listOf("mean", "variance"), decoder.expectedMetrics("average-std", null))
    }

    @Test
    fun `i bit di stato diventano testo, e quelli ignoti non spariscono`() {
        val flags = protocol.statusFlags(0b1_0000_0010)

        assertEquals(2, flags.size)
        assertEquals("config_pending", flags[0].meaning)
        assertEquals("event", flags[1].kind, "il reset e' un evento latchato, non uno stato")

        val ignoto = protocol.statusFlags(1 shl 4).single()
        assertEquals(4, ignoto.bit)
        assertTrue(ignoto.description.contains("non descritto"))
    }

    @Test
    fun `la parola di stato della MU usa la sua tabella`() {
        assertEquals("online", protocol.muStatusFlags(1 shl 7).single().meaning)
    }

    @Test
    fun `le sentinelle della batteria vengono dal dizionario`() {
        assertEquals("ac_powered", protocol.batteryMeaning(254))
        assertEquals("charging", protocol.batteryMeaning(255))
        assertNull(protocol.batteryMeaning(80))
    }
}
