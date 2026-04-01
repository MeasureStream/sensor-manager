package com.polito.tesi.measuremanager.unitTests.services

import com.polito.tesi.measuremanager.dtos.CuJoinNotification
import com.polito.tesi.measuremanager.dtos.CuStatusUpdate
import com.polito.tesi.measuremanager.dtos.MuDescriptor
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.hmac.NetworkIdEncoder
import com.polito.tesi.measuremanager.kafka.KafkaCuProducer
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import com.polito.tesi.measuremanager.template.TemplateService
import io.mockk.*
import jakarta.persistence.EntityNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.springframework.data.repository.findByIdOrNull


class ControlUnitServiceTest {

    private val cur = mockk<ControlUnitRepository>()
    private val mur = mockk<MeasurementUnitRepository>()
    private val ss = mockk<SecurityService>()
    private val kcu = mockk<KafkaCuProducer>()
    private val ts = mockk<TemplateService>()

    private val service = ControlUnitServiceImpl(cur, mur, ss, kcu, ts)

    // --- TEST: getControlUnit ---
    @Test
    fun `getControlUnit as Admin should return CU regardless of owner`() {
        val cu = ControlUnit().apply { id = 1; name = "Admin CU"; devEui = 123L }
        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(1L) } returns cu
        every { ts.getTemplate(any()) } returns mockk() // Per toDTO() se necessario

        val result = service.getControlUnit(1L)

        assertNotNull(result)
        assertEquals(123L, result.devEui)
        verify { cur.findByIdOrNull(1L) }
    }

    @Test
    fun `getControlUnit as User should throw exception if not owner`() {
        every { ss.isAdmin() } returns false
        every { ss.getCurrentUserId() } returns "user123"
        every { cur.findByIdAndUser_UserId(1L, "user123") } returns null

        assertThrows<EntityNotFoundException> {
            service.getControlUnit(1L)
        }
    }

    // --- TEST: claimControlUnit ---
    @Test
    fun `claimControlUnit should associate user to existing orphan CU`() {
        val devEui = 12345L
        val hash = NetworkIdEncoder.encode(devEui)
        val user = User().apply { userId = "new-owner" }
        val orphanCu = ControlUnit().apply { this.devEui = devEui; this.user = null; name = "Old" }

        every { ss.getOrCreateCurrentUser() } returns user
        every { cur.findByDevEui(devEui) } returns orphanCu
        every { cur.save(any()) } answers { firstArg() }

        val result = service.claimControlUnit(hash)

        assertEquals("new-owner", orphanCu.user?.userId)
        verify { cur.save(orphanCu) }
    }

    @Test
    fun `claimControlUnit should throw exception if CU already claimed`() {
        // GIVEN
        val devEui = 12345L
        val hash = NetworkIdEncoder.encode(devEui)
        val claimedCu = ControlUnit().apply {
            this.devEui = devEui
            this.user = User().apply { userId = "already-owner" }
        }

        // AGGIUNTA: Mockiamo anche l'utente, altrimenti MockK si arrabbia
        every { ss.getOrCreateCurrentUser() } returns User().apply { userId = "some-user" }

        every { cur.findByDevEui(devEui) } returns claimedCu

        // WHEN & THEN
        assertThrows<OperationNotAllowed> {
            service.claimControlUnit(hash)
        }
    }

    // --- TEST: onStatusUpdate (Heartbeat 0x0A) ---
    @Test
    fun `onStatusUpdate should update battery and model`() {
        val update = CuStatusUpdate(devEui = 111L, model = 5, batteryLevel = 75, statusRaw = 0)
        val existingCu = ControlUnit().apply { devEui = 111L; remainingBattery = 100.0 }

        every { cur.findByDevEui(111L) } returns existingCu
        every { cur.save(any()) } answers { firstArg() }

        service.onStatusUpdate(update)

        assertEquals(75.0, existingCu.remainingBattery)
        assertEquals(5, existingCu.model)
        verify { cur.save(existingCu) }
    }

    // --- TEST: onJoinNotification (Topology 0x10) - Pulizia ---
    @Test
    fun `onJoinNotification should perform cleanup of old MUs`() {
        val devEui = 100L
        val oldMu = MeasurementUnit().apply { extendedId = 555L }
        val cu = ControlUnit().apply {
            this.devEui = devEui
            this.measurementUnits = mutableListOf(oldMu)
        }
        oldMu.controlUnit = cu

        val notification = CuJoinNotification(devEui, listOf(MuDescriptor(666L, 1, 100)))

        every { cur.findByDevEui(devEui) } returns cu
        every { mur.findByExtendedId(666L) } returns null // Nuova MU
        every { cur.save(any()) } answers { firstArg() }
        every { mur.save(any()) } answers { firstArg() }

        service.onJoinNotification(notification)

        // Verifichiamo che la vecchia MU (555) sia stata scollegata
        assertEquals(null, oldMu.controlUnit)
        // Verifichiamo che la nuova MU (666) sia collegata
        assertEquals(1, cu.measurementUnits.size)
        assertEquals(666L, cu.measurementUnits[0].extendedId)
    }

    @Test
    fun `delete should send Kafka event and call repository`() {
        // 1. GIVEN
        // Creiamo un utente finto da assegnare alla CU
        val mockUser = User().apply {
            userId = "admin-id"
            email = "admin@polito.it"
        }

        val cu = ControlUnit().apply {
            id = 1L
            name = "To Delete"
            devEui = 999L
            user = mockUser // Assegniamo l'utente qui
            remainingBattery = 85.0
            rssi = -70.0
        }

        // Configurazione dei Mock
        every { ss.isAdmin() } returns true
        every { ss.getCurrentUserId() } returns "admin-id"
        every { cur.findById(1L) } returns java.util.Optional.of(cu)

        // Definiamo le azioni per i metodi che restituiscono Unit (void)
        every { cur.deleteById(1L) } just Runs
        every { kcu.sendCuCreate(any()) } just Runs

        // 2. WHEN
        service.delete(1L)

        // 3. THEN
        // Verifichiamo che la cancellazione sia avvenuta sul repository
        verify(exactly = 1) { cur.deleteById(1L) }

        // Verifichiamo che il messaggio inviato a Kafka sia di tipo DELETE
        verify(exactly = 1) {
            kcu.sendCuCreate(match { it.eventType == "DELETE" })
        }
    }

    @Test
    fun `delete should fail if user is not admin`() {
        every { ss.isAdmin() } returns false

        assertThrows<OperationNotAllowed> {
            service.delete(1L)
        }
    }


    @Test
    fun `onJoinNotification should create MU and sensors for model 1`() {
        // GIVEN
        val devEui = 12345L
        val muExtendedId = 999L
        val notification = CuJoinNotification(
            devEui = devEui,
            muList = listOf(MuDescriptor(muExtendedId, localId = 1, model = 1))
        )

        // Simuliamo che la CU non esista e la MU non esista
        every { cur.findByDevEui(devEui) } returns null
        every { mur.findByExtendedId(muExtendedId) } returns null

        // Slot per catturare cosa viene salvato
        val cuSlot = slot<ControlUnit>()
        val muSlot = slot<MeasurementUnit>()
        every { cur.save(capture(cuSlot)) } answers { firstArg() }
        every { mur.save(capture(muSlot)) } answers { firstArg() }

        // WHEN
        service.onJoinNotification(notification)

        // THEN
        verify(exactly = 2) { cur.save(any()) } // Una volta per getOrCreate, una alla fine
        verify(atLeast = 1) { mur.save(any()) }

        val savedMu = muSlot.captured
        assertEquals(4, savedMu.sensors.size) // Il modello 1 deve avere 4 sensori
        assertEquals("accelerometer_lsm6dsm", savedMu.sensors[0].modelName)
    }

}




