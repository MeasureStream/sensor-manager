package com.polito.tesi.measuremanager.unitTests.services

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.kafka.KafkaMuProducer
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.SensorAlarmRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.services.MeasurementUnitServiceImpl
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.SensorMapper
import com.polito.tesi.measuremanager.template.TemplateService
import java.time.OffsetDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.mockk.*
import org.junit.jupiter.api.assertThrows
import org.springframework.data.repository.findByIdOrNull
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MeasurementUnitServiceTest {

    private val mur = mockk<MeasurementUnitRepository>()
    private val kmu = mockk<KafkaMuProducer>()
    private val sensorMapper = mockk<SensorMapper>()
    private val templateService = mockk<TemplateService>()
    private val ss = mockk<SecurityService>()
    // Il dizionario di protocollo traduce i bit di stato: qui basta che non risolva niente.
    private val protocol = mockk<ProtocolService>(relaxed = true)
    private val deviceEvents = mockk<DeviceEventRepository>()
    private val alarms = mockk<SensorAlarmRepository>()

    private val service =
        MeasurementUnitServiceImpl(
            mur, kmu, sensorMapper, templateService, ss, protocol, deviceEvents, alarms,
        )

    // --- TEST: get ---
    @Test
    fun `get as admin should return any MU`() {
        val muId = 1L
        val mu = MeasurementUnit().apply { id = muId; extendedId = 12345L }
        val dto = MeasurementUnitDTO(id = muId, extendedId = 12345L, localId = 1, model = 1, controlUnitId = null)

        every { ss.isAdmin() } returns true
        every { mur.findByIdOrNull(muId) } returns mu
        every { sensorMapper.toUnitDTO(mu) } returns dto

        val result = service.get(muId)

        assertNotNull(result)
        assertEquals(12345L, result.extendedId)
        verify { mur.findByIdOrNull(muId) }
    }

    @Test
    fun `get as user should call repository with userId`() {
        val muId = 1L
        val userId = "user-789"

        every { ss.isAdmin() } returns false
        every { ss.getCurrentUserId() } returns userId
        every { mur.findByIdAndUser_UserId(muId, userId) } returns null

        service.get(muId)

        verify { mur.findByIdAndUser_UserId(muId, userId) }
    }

    // --- TEST: getAll ---
    @Test
    fun `getAll as admin with extendedId filter`() {
        val extId = 555L
        val muList = listOf(MeasurementUnit().apply { extendedId = extId })

        every { ss.isAdmin() } returns true
        every { mur.findAllByExtendedId(extId) } returns muList
        every { sensorMapper.toUnitDTO(any()) } returns mockk()

        service.getAll(extendedId = extId, controlUnitDevEUI = null, controlUnitName = null)

        verify { mur.findAllByExtendedId(extId) }
        verify(exactly = 0) { mur.findAll() }
    }

    @Test
    fun `getAll as user should only return owned units`() {
        val userId = "my-user"
        every { ss.isAdmin() } returns false
        every { ss.getCurrentUserId() } returns userId
        every { mur.findAllByUser_UserId(userId) } returns listOf()

        service.getAll(null, null, null)

        verify { mur.findAllByUser_UserId(userId) }
    }

    @Test
    fun `delete as admin should send kafka event and delete`() {
        // GIVEN
        val muId = 10L
        // Assicurati che l'entità abbia i campi necessari per toMUCreateDTO()
        val mu = MeasurementUnit().apply {
            id = muId
            extendedId = 999L
            model = 1
            // Senza proprietario toMUCreateDTO() torna null e l'evento non parte.
            user = User().apply { userId = "owner" }
        }

        // Mockiamo il comportamento dei repository e dei servizi
        every { mur.findById(muId) } returns Optional.of(mu)
        every { ss.isAdmin() } returns true
        every { mur.deleteById(muId) } just Runs
        every { kmu.sendMuCreate(any()) } just Runs
        every { deviceEvents.deleteByMeasurementUnit_Id(muId) } returns 3
        every { alarms.deleteByMeasurementUnit_Id(muId) } returns 1

        // WHEN
        service.delete(muId)

        // THEN
        verify(exactly = 1) { mur.deleteById(muId) }

        // Verifichiamo che l'evento sia stato inviato
        // Se continua a fallire, il problema è dentro mu.toMUCreateDTO()
        verify(exactly = 1) { kmu.sendMuCreate(any()) }

        // Eventi e allarmi si cancellano a mano: la chiave esterna li lascerebbe in piedi
        // con il riferimento azzerato, cioe' righe che non si sa piu' di chi siano.
        verify(exactly = 1) { deviceEvents.deleteByMeasurementUnit_Id(muId) }
        verify(exactly = 1) { alarms.deleteByMeasurementUnit_Id(muId) }
    }

    // --- TEST: l'elenco della pagina «Mie MU» ---

    @Test
    fun `l'elenco porta l'ultima CU anche quando la MU e' staccata`() {
        val cu = ControlUnit().apply { id = 7; name = "La mia CU"; deviceId = "lora-e5" }
        val staccata =
            MeasurementUnit().apply {
                id = 1
                extendedId = 0xAABBA1B2
                model = 1
                lastControlUnit = cu
                detachedAt = OffsetDateTime.now().minusDays(2)
                lastSeen = OffsetDateTime.now().minusDays(2)
            }

        every { ss.isAdmin() } returns true
        every { mur.findAllByArchivedAtIsNull() } returns listOf(staccata)
        every { deviceEvents.countOpenByMeasurementUnit(listOf(1L)) } returns emptyList()
        every { alarms.countOpenByMeasurementUnit(listOf(1L)) } returns emptyList()

        val riga = service.summaries().single()

        assertEquals("MU hA1B2", riga.label, "la stessa etichetta che compare nelle schede")
        assertFalse(riga.attached, "non e' montata su nessuna CU")
        assertFalse(riga.online, "staccata vuol dire non in uso, qualunque sia l'ultimo contatto")
        assertEquals(7L, riga.controlUnit?.id, "l'ultima CU resta leggibile dopo lo stacco")
    }

    @Test
    fun `una MU senza nessun contatto non risulta in uso`() {
        val mu = MeasurementUnit().apply { id = 2; extendedId = 1L; controlUnit = ControlUnit() }

        every { ss.isAdmin() } returns true
        every { mur.findAllByArchivedAtIsNull() } returns listOf(mu)
        every { deviceEvents.countOpenByMeasurementUnit(listOf(2L)) } returns emptyList()
        every { alarms.countOpenByMeasurementUnit(listOf(2L)) } returns emptyList()

        assertFalse(service.summaries().single().online)
    }

    // --- TEST: dissocia ---

    @Test
    fun `dissociare toglie dall'elenco senza cancellare niente`() {
        val mu = MeasurementUnit().apply { id = 3; extendedId = 42L }

        every { ss.isAdmin() } returns true
        every { mur.findByIdOrNull(3L) } returns mu
        every { mur.save(any()) } answers { firstArg() }

        val riga = service.dissociate(3L)

        assertTrue(riga.archived)
        assertNotNull(mu.archivedAt)
        verify(exactly = 0) { mur.deleteById(any()) }
        verify(exactly = 0) { deviceEvents.deleteByMeasurementUnit_Id(any()) }
    }

    @Test
    fun `delete as non-admin should throw OperationNotAllowed`() {
        val muId = 10L
        val mu = MeasurementUnit().apply { id = muId }

        every { mur.findById(muId) } returns Optional.of(mu)
        every { ss.isAdmin() } returns false

        assertThrows<OperationNotAllowed> {
            service.delete(muId)
        }

        verify(exactly = 0) { mur.deleteById(any()) }
    }

    // --- TEST: update (caso TODO) ---
    @Test
    fun `update should throw TODO exception`() {
        assertThrows<NotImplementedError> {
            service.update(1L, mockk())
        }
    }
}
