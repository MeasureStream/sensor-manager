package com.polito.tesi.measuremanager.unitTests.services

import com.polito.tesi.measuremanager.dtos.LoraUplink
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.EventOrigin
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.repositories.SensorAlarmRepository
import com.polito.tesi.measuremanager.services.AlarmService
import com.polito.tesi.measuremanager.template.AlarmDecoder
import com.polito.tesi.measuremanager.template.DecodedEvent
import com.polito.tesi.measuremanager.template.DecodedEventMessage
import com.polito.tesi.measuremanager.template.MuModelService
import com.polito.tesi.measuremanager.template.SensorConversion
import com.polito.tesi.measuremanager.template.TemplateService
import io.mockk.*
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Un fatto che dura si racconta una volta sola.
 *
 * Gli eventi della 0xA2 non hanno un rientro: la CU li rimanda a ogni messaggio finche' la
 * condizione c'e'. Senza il controllo sull'avviso gia' aperto, una comunicazione fallita
 * diventava una riga per uplink e una fila di badge identici in cima alla scheda.
 */
class AlarmServiceTest {

    private val cur = mockk<ControlUnitRepository>()
    private val alarms = mockk<SensorAlarmRepository>()
    private val events = mockk<DeviceEventRepository>()
    private val decoder = mockk<AlarmDecoder>()
    private val ts = mockk<TemplateService>(relaxed = true)
    private val muModel = mockk<MuModelService>(relaxed = true)
    private val conversion = mockk<SensorConversion>(relaxed = true)

    private val service = AlarmService(cur, alarms, events, decoder, ts, muModel, conversion)

    private fun cu() =
            ControlUnit().apply {
                id = 7
                devEui = 111L
                deviceId = "cu-0001001"
                name = "CU di prova"
            }

    /** Un uplink qualunque: i byte non si leggono mai, il decoder qui e' simulato. */
    private fun uplink() =
            LoraUplink(
                    devEui = 111L,
                    deviceId = "cu-0001001",
                    fport = 0xA2,
                    rawPayload = Base64.getEncoder().encodeToString(byteArrayOf(1, 1)),
                    timestamp = null,
            )

    /** Lo stesso guasto, sullo stesso canale, con il contatore dei fallimenti cresciuto. */
    private fun evento(param: Int) =
            DecodedEventMessage(
                    eventSeq = 1,
                    entries =
                            listOf(
                                    DecodedEvent(
                                            source = 1,
                                            sensorIndex = 2,
                                            code = 0x21,
                                            param = param,
                                            ageRaw = 0,
                                            elapsed = null,
                                            description =
                                                    "Comunicazione col sensore fallita ($param fallimenti consecutivi)",
                                    )
                            ),
            )

    @Test
    fun `lo stesso evento ancora aperto non diventa una riga nuova`() {
        val cu = cu()
        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { decoder.decodeEvents(any(), any()) } returns evento(5) andThen evento(7)

        // Il doppio del repository: quello che si salva e' quello che si ritrova aperto.
        var aperto: DeviceEvent? = null
        every { events.save(any<DeviceEvent>()) } answers
                {
                    firstArg<DeviceEvent>().also { aperto = it }
                }
        every {
            events.findFirstByControlUnit_IdAndOriginAndCodeAndSourceAndSensorIndexAndAcknowledgedAtIsNull(
                    7L, EventOrigin.DEVICE, 0x21, 1, 2)
        } answers { aperto }

        service.onEvents(uplink())
        assertEquals(1, aperto?.occurrences, "la prima volta nasce la riga")

        service.onEvents(uplink())

        val riga = assertNotNull(aperto)
        assertEquals(2, riga.occurrences, "la seconda si conta, non si duplica")
        assertEquals(
                "Comunicazione col sensore fallita (7 fallimenti consecutivi)",
                riga.description,
                "il parametro cresciuto aggiorna il testo: e' lo stesso fatto, peggiorato",
        )
        assertEquals(7, riga.param)
    }

    @Test
    fun `lo stesso codice su un altro canale resta un altro fatto`() {
        val cu = cu()
        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { decoder.decodeEvents(any(), any()) } returns evento(5)
        every { events.save(any<DeviceEvent>()) } answers { firstArg() }
        // Aperto non c'e' niente per il canale 2, anche se lo stesso codice fosse aperto
        // altrove: la domanda che il servizio pone comprende il soggetto.
        every {
            events.findFirstByControlUnit_IdAndOriginAndCodeAndSourceAndSensorIndexAndAcknowledgedAtIsNull(
                    7L, EventOrigin.DEVICE, 0x21, 1, 2)
        } returns null

        service.onEvents(uplink())

        verify {
            events.findFirstByControlUnit_IdAndOriginAndCodeAndSourceAndSensorIndexAndAcknowledgedAtIsNull(
                    7L, EventOrigin.DEVICE, 0x21, 1, 2)
        }
        val scritto = slot<DeviceEvent>()
        verify { events.save(capture(scritto)) }
        assertEquals(2, scritto.captured.sensorIndex, "il canale fa parte dell'identita'")
        assertEquals(1, scritto.captured.occurrences)
    }
}
