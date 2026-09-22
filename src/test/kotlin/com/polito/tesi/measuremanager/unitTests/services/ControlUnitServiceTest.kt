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
import com.polito.tesi.measuremanager.kafka.LorawanPayloadEncoder
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.MetricSampleRepository
import com.polito.tesi.measuremanager.repositories.UplinkFrameRepository
import com.polito.tesi.measuremanager.repositories.SignalQualityRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.template.MuModelService
import com.polito.tesi.measuremanager.template.ProtocolService
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
    private val mr = mockk<MeasurementRepository>()
    private val sqr = mockk<SignalQualityRepository>()
    private val ss = mockk<SecurityService>()
    private val kcu = mockk<KafkaCuProducer>()
    private val ts = mockk<TemplateService>()
    private val muModel = mockk<MuModelService>()
    // Il dizionario di protocollo serve solo allo stato online: qui basta che non risolva.
    private val protocol = mockk<ProtocolService>(relaxed = true)
    private val frames = mockk<UplinkFrameRepository>(relaxed = true)
    private val samples = mockk<MetricSampleRepository>(relaxed = true)
    private val encoder = mockk<LorawanPayloadEncoder>()

    private val service = ControlUnitServiceImpl(cur, mur, mr, sqr, ss, kcu, ts, muModel, protocol, frames, samples, encoder)

    /** Gli slot dello 0x0001 v0.1.0 come li materializza il registro: indici da 0. */
    private fun mu0001(extendedId: Long, localId: Int) =
            MeasurementUnit().apply {
                this.extendedId = extendedId
                this.model = 1
                this.localId = localId
                this.sensors = mutableListOf()
                listOf(
                                "AccelerometerLSM6DSM",
                                "PressureSensorMS5837",
                                "HumiditySensorHTU21D",
                                "TemperatureSensorNTC",
                        )
                        .forEachIndexed { index, name ->
                            sensors.add(
                                    Sensor(
                                            modelName = name,
                                            measurementUnit = this,
                                            sensorIndex = index,
                                            configurationMeasure = "average-std",
                                    )
                            )
                        }
            }

    // --- TEST: getControlUnit ---
    @Test
    fun `getControlUnit as Admin should return CU regardless of owner`() {
        val cu = ControlUnit().apply { id = 1; name = "Admin CU"; devEui = 123L; deviceId = "lora-e5" }
        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(1L) } returns cu
        // toDTO chiede il documento al registro: qui non serve, il DTO esce con template = null
        every { ts.getDocument(any()) } returns null

        val result = service.getControlUnit(1L)

        assertNotNull(result)
        assertEquals("123", result.devEui)
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
        val orphanCu = ControlUnit().apply { this.devEui = devEui; this.deviceId = "lora-e5"; this.user = null; name = "Old" }

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
        val update = CuStatusUpdate(
                devEui = 111L,
                deviceId = "lora-e5",
                model = 5,
                batteryLevel = 75,
                ptx = 14,
                acPowered = false,
                isCharging = false,
                statusRaw = 0,
        )
        val existingCu = ControlUnit().apply { devEui = 111L; deviceId = "lora-e5"; remainingBattery = 100.0 }

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
            this.deviceId = "lora-e5"
            this.measurementUnits = mutableListOf(oldMu)
        }
        oldMu.controlUnit = cu

        val notification = CuJoinNotification(devEui, "lora-e5", listOf(MuDescriptor(666L, 1, 100)))

        every { cur.findByDevEui(devEui) } returns cu
        every { mur.findByExtendedId(666L) } returns null // Nuova MU
        every { muModel.createMeasurementUnit(666L, 100, 1) } returns mu0001(666L, 1)
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
            deviceId = "lora-e5",
            muList = listOf(MuDescriptor(muExtendedId, localId = 1, model = 1))
        )

        // Simuliamo che la CU non esista e la MU non esista
        every { cur.findByDevEui(devEui) } returns null
        every { mur.findByExtendedId(muExtendedId) } returns null
        every { muModel.createMeasurementUnit(muExtendedId, 1, 1) } returns mu0001(muExtendedId, 1)

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
        // Lo 0x0001 v0.1.0 ha quattro slot e gli indici partono da 0.
        assertEquals(4, savedMu.sensors.size)
        assertEquals("AccelerometerLSM6DSM", savedMu.sensors[0].modelName)
        assertEquals(0, savedMu.sensors[0].sensorIndex)
    }

}




