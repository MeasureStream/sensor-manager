package com.polito.tesi.measuremanager.unitTests.template

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.template.ContentFlag
import com.polito.tesi.measuremanager.template.MetrologySpec
import com.polito.tesi.measuremanager.template.ProtocolDictionary
import com.polito.tesi.measuremanager.template.SensorTemplate
import com.polito.tesi.measuremanager.template.TemplateParser
import com.polito.tesi.measuremanager.template.TemplateRejected
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.assertThrows

/**
 * I documenti veri del repository sensor-templates, letti come li legge il server: schema
 * 2.2.0 dei sensori, dizionario 1.2.3 con il byte di eta', procedura 401. Se il repository
 * non e' accanto, i test si saltano invece di fallire.
 */
class PublishedTemplatesTest {

    private val mapper =
            ObjectMapper().registerKotlinModule().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun file(path: String): File =
            File("../sensor-templates/$path").also { assumeTrue(it.exists(), "sensor-templates non accanto") }

    @Test
    fun `schema 2_2_0, l'MPE dei tre sensori`() {
        fun spec(name: String) = MetrologySpec.of(mapper.readValue(file("sensors/$name"), SensorTemplate::class.java).metrology)

        assertEquals("mpe", spec("ntc_temperature.json").mpeSource)
        assertEquals(0.5, spec("ntc_temperature.json").mpeAt(20.0))
        assertEquals(5.0, spec("humidity_hpp845e.json").mpeAt(50.0))
        assertNotNull(spec("humidity_hpp845e.json").drift)
        assertEquals(true, spec("pressure_ms5837.json").typeAFromVariance)
    }

    @Test
    fun `dizionario 1_2_3, il bit di eta'`() {
        val dictionary = mapper.readValue(file("protocol/protocol_v1_2.json"), ProtocolDictionary::class.java)
        val age = dictionary.contentFlags.single { it.name == ContentFlag.AGE }
        assertEquals(3, age.bit)
        assertEquals(0, age.fragment)
        assert(dictionary.contentClasses.none { it.bit == age.bit }) { "il bit di eta' non puo' essere anche una classe" }
    }

    @Test
    fun `la procedura 401 passa il controllo del registro`() {
        val doc = TemplateParser.parse(file("procedures/procedure_401_pozzetto.json").readText(), mapper, TemplateKind.PROCEDURE)
        assertEquals(TemplateKind.PROCEDURE, doc.kind)
        assertEquals(401, doc.keyId)
    }

    @Test
    fun `una procedura senza punti viene rifiutata`() {
        val bad = """{"kind":"procedure","templateId":402,"templateVersion":"1.0.0","quantity":"temperature","points":[]}"""
        assertThrows<TemplateRejected> { TemplateParser.parse(bad, mapper) }
        val profile = """{"kind":"profile","templateId":501,"templateVersion":"1.0.0","quantity":"temperature","limits":{"low":5,"high":-18}}"""
        assertThrows<TemplateRejected> { TemplateParser.parse(profile, mapper) }
    }
}
