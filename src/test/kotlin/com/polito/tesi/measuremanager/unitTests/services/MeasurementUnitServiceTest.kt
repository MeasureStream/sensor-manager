package com.polito.tesi.measuremanager.unitTests.services

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.kafka.KafkaMuProducer
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.services.MeasurementUnitServiceImpl
import com.polito.tesi.measuremanager.template.SensorMapper
import com.polito.tesi.measuremanager.template.TemplateService
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

    private val service = MeasurementUnitServiceImpl(mur, kmu, sensorMapper, templateService, ss)

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
        }

        // Mockiamo il comportamento dei repository e dei servizi
        every { mur.findById(muId) } returns Optional.of(mu)
        every { ss.isAdmin() } returns true
        every { mur.deleteById(muId) } just Runs
        every { kmu.sendMuCreate(any()) } just Runs

        // WHEN
        service.delete(muId)

        // THEN
        verify(exactly = 1) { mur.deleteById(muId) }

        // Verifichiamo che l'evento sia stato inviato
        // Se continua a fallire, il problema è dentro mu.toMUCreateDTO()
        verify(exactly = 1) { kmu.sendMuCreate(any()) }
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
