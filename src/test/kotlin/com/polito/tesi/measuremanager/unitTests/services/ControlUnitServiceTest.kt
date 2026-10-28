package com.polito.tesi.measuremanager.unitTests.services

import com.polito.tesi.measuremanager.dtos.CuJoinNotification
import com.polito.tesi.measuremanager.dtos.CUTransmissionCommandDTO
import com.polito.tesi.measuremanager.dtos.CuMeasuresUpdate
import com.polito.tesi.measuremanager.dtos.CuStatusUpdate
import com.polito.tesi.measuremanager.dtos.MuDescriptor
import com.polito.tesi.measuremanager.dtos.ProcessingCommandDTO
import com.polito.tesi.measuremanager.dtos.SamplingCommandDTO
import com.polito.tesi.measuremanager.dtos.SensorConfigCommandSetDTO
import com.polito.tesi.measuremanager.dtos.SensorSelectorDTO
import com.polito.tesi.measuremanager.entities.ConfigSnapshot
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.UplinkFrame
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.hmac.NetworkIdEncoder
import com.polito.tesi.measuremanager.kafka.EncodedPayload
import com.polito.tesi.measuremanager.kafka.KafkaCuProducer
import com.polito.tesi.measuremanager.kafka.LorawanPayloadEncoder
import com.polito.tesi.measuremanager.uplink.FPort
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.repositories.MetricSampleRepository
import com.polito.tesi.measuremanager.repositories.SensorAlarmRepository
import com.polito.tesi.measuremanager.repositories.UplinkFrameRepository
import com.polito.tesi.measuremanager.repositories.SignalQualityRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.EventOrigin
import com.polito.tesi.measuremanager.entities.EventSeverity
import com.polito.tesi.measuremanager.entities.ServerEventCode
import com.polito.tesi.measuremanager.services.ControlUnitServiceImpl
import com.polito.tesi.measuremanager.services.ServerEventRecorder
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.template.MuModelService
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.ConfigSnapshotService
import com.polito.tesi.measuremanager.template.ReportDecoder
import com.polito.tesi.measuremanager.template.ReportTruncated
import com.polito.tesi.measuremanager.template.StatusBit
import com.polito.tesi.measuremanager.sensorconfig.SensorConfigPlanner
import com.polito.tesi.measuremanager.template.TemplateService
import io.mockk.*
import jakarta.persistence.EntityNotFoundException
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    /* Come per gli eventi: il tipo generico di `save` va risolto, o il cast fallisce. */
    private val frames = mockk<UplinkFrameRepository>(relaxed = true).also {
        every { it.save(any<UplinkFrame>()) } answers { firstArg() }
    }
    private val samples = mockk<MetricSampleRepository>(relaxed = true)
    private val reportDecoder = mockk<ReportDecoder>(relaxed = true)
    private val configSnapshots = mockk<ConfigSnapshotService>(relaxed = true)
    private val alarmRepo = mockk<SensorAlarmRepository>(relaxed = true)
    /*
     * Il taccuino degli eventi lo tocca quasi ogni poll, quindi la risposta di `save` serve
     * ovunque: un simulacro rilassato restituirebbe un Object e il cast al tipo generico
     * fallirebbe in metodi che con gli eventi non c'entrano niente.
     */
    private val eventRepo = mockk<DeviceEventRepository>(relaxed = true).also {
        every { it.save(any<DeviceEvent>()) } answers { firstArg() }
    }
    private val encoder = mockk<LorawanPayloadEncoder>()

    /**
     * Il pianificatore non ha stato e dipende solo dal dizionario: si usa quello vero, cosi'
     * le prove verificano anche l'espansione dei selettori invece di fidarsi di un simulacro.
     */
    private val planner = SensorConfigPlanner(protocol)

    /** Anche il taccuino degli eventi e' quello vero: la regola che conta sta dentro di lui. */
    private val serverEvents = ServerEventRecorder(eventRepo)

    private val service = ControlUnitServiceImpl(
                    cur, mur, mr, sqr, ss, kcu, ts, muModel, protocol, frames, samples,
                    reportDecoder, configSnapshots, alarmRepo, eventRepo, encoder, planner,
                    serverEvents,
            )

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

    /* --------------------------------------------------------------- CFG_VER */

    /**
     * La CU incrementa il CFG_VER per ogni comando con prologo che applica, 0x24 compresa.
     * Se il server non lo segue resta indietro di uno, e il primo report successivo dichiara
     * una versione che il server non conosce: scartato, e per sempre.
     */
    @Test
    fun `la programmazione breve incrementa il CFG_VER e fotografa la configurazione`() {
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            configVersion = 4
            transmissionInterval = 0
        }

        every { ss.isAdmin() } returns true
        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        // Il CMD_SEQ lo assegna il database con una istruzione sola: qui si simula il giro.
        every { cur.bumpCmdSeq(any()) } returns 1
        every { cur.readCmdSeq(any()) } returns 7
        every { encoder.encodeTransmissionConfig(any(), any()) } returns emptyList()

        service.sendTransmissionCommand(CUTransmissionCommandDTO("111", 1))

        assertEquals(5, cu.configVersion, "il comando e' stato applicato: il contatore sale")
        verify(exactly = 1) { configSnapshots.take(cu) }
    }

    @Test
    fun `se la CU conferma l'ultimo comando inviato il server si allinea`() {
        // Il caso reale: due 0x24 applicate dalla CU e non contate dal server. Il CMD_SEQ
        // restituito e' quello che il server ha mandato, quindi la configurazione in opera e'
        // la sua: cambia solo il numero con cui chiamarla.
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            configVersion = 0
            cmdSeq = 3
            // Prima del rientro c'erano report scartati: l'avviso arancione era acceso.
            configMismatchCount = 7
            lastConfigMismatchAt = OffsetDateTime.now()
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }

        service.onStatusUpdate(poll(configVersion = 2, appliedCmdSeq = 3))

        assertEquals(2, cu.configVersion, "il server adotta il numero dichiarato dalla CU")
        assertEquals(3, cu.appliedCmdSeq)
        verify(exactly = 1) { configSnapshots.take(cu) }

        // Il disallineamento e' rientrato adesso, non al prossimo report: l'avviso dei
        // report scartati deve spegnersi subito, o la scheda continua a dire una cosa falsa.
        assertEquals(0L, cu.configMismatchCount)
        assertNull(cu.lastConfigMismatchAt)
        assertNull(cu.lastReportedConfigVersion, "non c'e' piu' nessun disallineamento da ricordare")

        // E il rientro si racconta, in chiaro: e' una buona notizia, non un avviso.
        verify {
            eventRepo.save(match<DeviceEvent> {
                it.code == ServerEventCode.CONFIG_REALIGNED && it.severity == EventSeverity.INFO
            })
        }
    }

    @Test
    fun `se la CU conferma un comando diverso il server non tocca niente`() {
        // Un downlink non e' arrivato: la configurazione attiva sulla CU e' una vecchia, e i
        // suoi report vanno letti con l'istantanea di allora, non con la mappa corrente.
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            configVersion = 7
            cmdSeq = 4
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }

        service.onStatusUpdate(poll(configVersion = 5, appliedCmdSeq = 3))

        assertEquals(7, cu.configVersion, "il server resta sulla configurazione che ha inviato")
        assertEquals(3, cu.appliedCmdSeq, "ma registra quale comando la CU ha davvero applicato")
        verify(exactly = 0) { configSnapshots.take(any()) }
    }

    @Test
    fun `senza CMD_SEQ nel poll non si adotta nessun numero`() {
        // Firmware precedente: il poll a nove byte non lo dichiara, e senza quel byte il
        // server puo' solo constatare il disallineamento.
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            configVersion = 0
            cmdSeq = 3
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }

        service.onStatusUpdate(poll(configVersion = 2, appliedCmdSeq = null))

        assertEquals(0, cu.configVersion)
        verify(exactly = 0) { configSnapshots.take(any()) }
    }

    @Test
    fun `su una CU mai configurata non c'e' nessun comando su cui essere d'accordo`() {
        // cmdSeq a 0: il server non ha mai mandato niente, quindi un CMD_SEQ applicato a 0
        // non conferma proprio nulla.
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            configVersion = 0
            cmdSeq = 0
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }

        service.onStatusUpdate(poll(configVersion = 4, appliedCmdSeq = 0))

        assertEquals(0, cu.configVersion)
        verify(exactly = 0) { configSnapshots.take(any()) }
    }

    /* ------------------------------------------- ultimo contatto e sanita' MU */

    @Test
    fun `l'ultimo contatto si aggiorna su qualunque uplink`() {
        val cu = ControlUnit().apply { devEui = 111L; deviceId = "lora-e5" }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }

        service.touch(111L, "2026-10-22T10:15:30Z")

        assertEquals(2026, cu.lastSeen?.year)
        assertEquals(10, cu.lastSeen?.hour, "l'istante e' UTC, come quello che arriva da TTN")
    }

    @Test
    fun `l'ultimo contatto non torna indietro`() {
        // Rileggendo un arretrato di Kafka i messaggi vecchi non devono far sembrare la CU
        // ferma a ore prima.
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            lastSeen = java.time.LocalDateTime.parse("2026-10-22T12:00:00")
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }

        service.touch(111L, "2026-10-22T09:00:00Z")

        assertEquals(12, cu.lastSeen?.hour)
        verify(exactly = 0) { cur.save(any()) }
    }

    @Test
    fun `il controllo di sanita' delle MU parte con il bit 0 di Command1`() {
        val cu = ControlUnit().apply {
            id = 7
            devEui = 111L
            deviceId = "lora-e5"
            name = "LabMS"
            pollingInterval = 1
            setting1 = 0
        }

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(7L) } returns cu
        every { encoder.encodeCuConfig(any(), any(), any(), any()) } returns emptyList()
        every { ts.getTemplate(any()) } returns null
        every { ts.getDocument(any()) } returns null

        service.requestMuCheck(7L)

        verify { encoder.encodeCuConfig(1, any(), 0, 1) }
    }

    @Test
    fun `senza periodo di poll si usa il valore di prova, e lo si registra`() {
        // Zero non si manda: per la documentazione e' lo spegnimento irreversibile. Il
        // valore di prova e' una scorciatoia dichiarata (STATO_LAVORI.md): si scrive sulla
        // CU perche' il modello del server resti uguale a cio' che il dispositivo applica,
        // e perche' succeda una volta sola.
        val cu = ControlUnit().apply {
            id = 7
            devEui = 111L
            deviceId = "lora-e5"
            name = "Mai configurata"
            pollingInterval = 0
        }

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(7L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { encoder.encodeCuConfig(any(), any(), any(), any()) } returns emptyList()
        every { ts.getTemplate(any()) } returns null
        every { ts.getDocument(any()) } returns null

        service.requestMuCheck(7L)

        assertEquals(1, cu.pollingInterval, "il valore inventato si registra, non resta implicito")
        verify { encoder.encodeCuConfig(1, any(), any(), 1) }
    }

    @Test
    fun `il bit di topologia cambiata chiede la sanita' una volta sola`() {
        val cu = ControlUnit().apply {
            devEui = 111L
            deviceId = "lora-e5"
            pollingInterval = 1
            statusWord = null
        }
        val cambiata = listOf(
                StatusBit(bit = 12, kind = "event", meaning = "topology_changed",
                        description = "Topologia MU cambiata"))

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.statusFlags(null) } returns emptyList()
        every { protocol.statusFlags(0x1000) } returns cambiata
        every { encoder.encodeCuConfig(any(), any(), any(), any()) } returns emptyList()

        // Primo poll con il bit alzato: e' un fronte, si chiede.
        service.onStatusUpdate(poll(configVersion = 0, statusRaw = 0x1000))
        verify(exactly = 1) { encoder.encodeCuConfig(any(), any(), any(), 1) }

        // Il bit e' latchato e resta alzato: non si richiede a ogni poll, altrimenti sarebbe
        // un downlink al minuto.
        service.onStatusUpdate(poll(configVersion = 0, statusRaw = 0x1000))
        verify(exactly = 1) { encoder.encodeCuConfig(any(), any(), any(), 1) }
    }

    // --- TEST: ripristino automatico dopo un riavvio ---

    @Test
    fun `dopo un riavvio la configurazione si rimanda, se l'interruttore e' acceso`() {
        val cu = cuConfigurabile().apply {
            configVersion = 9
            autoRestoreConfig = true
            // L'ultimo poll diceva: non ho configurazione.
            lastReportedConfigVersion = 0
        }
        cu.measurementUnits.flatMap { it.sensors }.forEachIndexed { i, s ->
            s.id = (i + 1).toLong()
            s.samplingF = if (s.sensorIndex == 0) 0.0 else 47.0
        }

        every { ss.isAdmin() } returns true
        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { mur.save(any()) } answers { firstArg() }
        // Le MU ci sono gia': la 0x11 dopo il riavvio le ritrova, non le crea. L'elenco si
        // cattura adesso perche' onJoinNotification lo svuota prima di ricostruirlo.
        val muDiPartenza = cu.measurementUnits.toList()
        every { mur.findByExtendedId(any()) } answers {
            val id = firstArg<Long>()
            muDiPartenza.find { it.extendedId == id }
        }
        every { muModel.materialize(any()) } returns false
        every { protocol.dictionary(any()) } returns null
        every { cur.bumpCmdSeq(any()) } returns 1
        every { cur.readCmdSeq(any()) } returnsMany listOf(21, 22)
        every { encoder.encodePeriods(any(), any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(1), FPort.PERIODS))
        every { encoder.encodeStatsAndThresholds(any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(2), FPort.STATS))
        every { kcu.sendDownlink(any(), any()) } returns Unit
        // L'istantanea della configurazione corrente cita gli stessi sensori di adesso.
        every { configSnapshots.forWireVersion(42L, 9) } returns
                snapshotConGliStessiSensori(cu)

        // La topologia conferma le MU: e' li' che la condizione diventa verificabile.
        service.onJoinNotification(topologia(cu))

        verify(exactly = 2) { kcu.sendDownlink("cu-0001001", any()) }
        assertEquals(11L, cu.configVersion, "due comandi rimandati, due incrementi attesi")
    }

    @Test
    fun `con l'interruttore spento non si rimanda niente`() {
        val cu = cuConfigurabile().apply {
            configVersion = 9
            autoRestoreConfig = false
            lastReportedConfigVersion = 0
        }
        cu.measurementUnits.flatMap { it.sensors }.forEach { it.samplingF = 47.0 }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { mur.save(any()) } answers { firstArg() }
        // Le MU ci sono gia': la 0x11 dopo il riavvio le ritrova, non le crea. L'elenco si
        // cattura adesso perche' onJoinNotification lo svuota prima di ricostruirlo.
        val muDiPartenza = cu.measurementUnits.toList()
        every { mur.findByExtendedId(any()) } answers {
            val id = firstArg<Long>()
            muDiPartenza.find { it.extendedId == id }
        }
        every { muModel.materialize(any()) } returns false

        service.onJoinNotification(topologia(cu))

        verify(exactly = 0) { kcu.sendDownlink(any(), any()) }
    }

    @Test
    fun `se la mappa delle MU e' cambiata il ripristino non si fa`() {
        // Due MU dello stesso modello hanno la stessa forma ma non sono lo stesso
        // dispositivo: rimandare la configurazione di prima vorrebbe dire configurare
        // sensori diversi da quelli che l'utente aveva scelto.
        val cu = cuConfigurabile().apply {
            configVersion = 9
            autoRestoreConfig = true
            lastReportedConfigVersion = 0
        }
        cu.measurementUnits.flatMap { it.sensors }.forEachIndexed { i, s ->
            s.id = (i + 1).toLong()
            s.samplingF = 47.0
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { mur.save(any()) } answers { firstArg() }
        // Le MU ci sono gia': la 0x11 dopo il riavvio le ritrova, non le crea. L'elenco si
        // cattura adesso perche' onJoinNotification lo svuota prima di ricostruirlo.
        val muDiPartenza = cu.measurementUnits.toList()
        every { mur.findByExtendedId(any()) } answers {
            val id = firstArg<Long>()
            muDiPartenza.find { it.extendedId == id }
        }
        every { muModel.materialize(any()) } returns false
        every { protocol.dictionary(any()) } returns null
        // L'istantanea cita sensori che adesso non ci sono piu'.
        every { configSnapshots.forWireVersion(42L, 9) } returns
                ConfigSnapshot(
                        controlUnit = cu,
                        cfgVersion = 9,
                        content = mapOf("slots" to listOf(mapOf("sensorId" to 99))),
                )

        service.onJoinNotification(topologia(cu))

        verify(exactly = 0) { kcu.sendDownlink(any(), any()) }
    }

    /** L'istantanea che cita esattamente i sensori che la CU ha adesso. */
    private fun snapshotConGliStessiSensori(cu: ControlUnit) =
            ConfigSnapshot(
                    controlUnit = cu,
                    cfgVersion = cu.configVersion,
                    content =
                            mapOf(
                                    "slots" to
                                            cu.measurementUnits.sortedBy { it.localId }.flatMap { mu ->
                                                mu.sensors.sortedBy { it.sensorIndex }.map {
                                                    mapOf("sensorId" to it.id)
                                                }
                                            }
                            ),
            )

    /** La notifica di topologia con le MU che la CU ha gia'. */
    private fun topologia(cu: ControlUnit) =
            CuJoinNotification(
                    devEui = 111L,
                    deviceId = cu.deviceId,
                    muList =
                            cu.measurementUnits.map {
                                MuDescriptor(it.extendedId ?: 0L, it.localId, it.model, it.modelMajor)
                            },
            )

    @Test
    fun `un report con il CFG_VER vecchio fa ripartire la configurazione, una volta sola`() {
        /*
         * Il report porta il CFG_VER della configurazione con cui e' stato prodotto: se e'
         * vecchio, il comando non e' entrato in opera. Arriva a ogni periodo di trasmissione,
         * quindi e' il segnale piu' tempestivo — ma proprio per questo il ripristino deve
         * scattare una volta per episodio e non a ogni report, o sarebbe un downlink ogni
         * quindici secondi. Il limite e' l'evento aperto nel taccuino.
         */
        val cu = cuConfigurabile().apply {
            configVersion = 9
            autoRestoreConfig = true
        }
        cu.measurementUnits.flatMap { it.sensors }.forEachIndexed { i, s ->
            s.id = (i + 1).toLong()
            s.samplingF = 47.0
        }
        val aperto = DeviceEvent(
                id = 1,
                controlUnit = cu,
                origin = EventOrigin.SERVER,
                code = ServerEventCode.CONFIG_NOT_APPLIED,
        )

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { ts.getDocument(any()) } returns null
        every { ts.getTemplate(any()) } returns null
        every { cur.bumpCmdSeq(any()) } returns 1
        every { cur.readCmdSeq(any()) } returnsMany listOf(21, 22, 23, 24)
        every { encoder.encodePeriods(any(), any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(1), FPort.PERIODS))
        every { encoder.encodeStatsAndThresholds(any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(2), FPort.STATS))
        every { kcu.sendDownlink(any(), any()) } returns Unit
        // Della versione 7 non c'e' istantanea: il report si scarta. Delle altre si', e serve
        // a verificare che la mappa delle MU sia rimasta la stessa.
        every { configSnapshots.forWireVersion(42L, any()) } answers {
            if (secondArg<Int>() == 7) null else snapshotConGliStessiSensori(cu)
        }
        // La prima volta non c'e' nessun episodio aperto; dalla seconda si'.
        every {
            eventRepo.findByControlUnit_IdAndOriginAndCodeAndAcknowledgedAtIsNull(
                    42L, EventOrigin.SERVER, ServerEventCode.CONFIG_NOT_APPLIED)
        } returnsMany listOf(emptyList(), listOf(aperto))

        service.onMeasuresUpdate(reportConCfgVer(7))
        service.onMeasuresUpdate(reportConCfgVer(7))

        // Due downlink in tutto: la 0x21 e la 0x23 del primo ripristino, non del secondo.
        verify(exactly = 2) { kcu.sendDownlink("cu-0001001", any()) }
    }

    private fun reportConCfgVer(cfgVersion: Int) =
            CuMeasuresUpdate(
                    devEui = 111L,
                    deviceId = "cu-0001001",
                    configVersion = cfgVersion,
                    rawPayload = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0, 1, 0, 2)),
                    timestamp = null,
                    content = 0x01,
            )

    // --- TEST: lettura del report ---

    @Test
    fun `nel report i sensori spenti non occupano posto`() {
        /*
         * Lo pseudocodice di decodifica della 0x30 e' esplicito: si legge uno slot «se il
         * sensore e' attivo (Sampl. T diverso da 0)». Il server contava anche gli spenti, e
         * su due MU con l'accelerometro a OFF si aspettava 8 slot invece di 6: dopo
         * ventiquattro byte finiva il payload e il report veniva scartato per intero, ogni
         * quindici secondi.
         */
        val cu = cuConfigurabile().apply {
            configVersion = 5
            measurementUnits = mutableListOf(mu0001(65537L, 1), mu0001(65538L, 2))
        }
        // Accelerometro spento, gli altri tre slot a 2 s: la configurazione della prova.
        cu.measurementUnits.flatMap { it.sensors }.forEach {
            it.samplingF = if (it.sensorIndex == 0) 0.0 else 47.0
        }

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { ts.getDocument(any()) } returns null
        every { ts.getTemplate(any()) } returns null
        every { reportDecoder.expectedMetrics(any(), any()) } returns listOf("mean", "variance")
        // Un simulacro del decoder che consuma i byte come farebbe quello vero.
        every { reportDecoder.decodeSlot(any(), any(), any(), any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            if (buffer.remaining() < 4) {
                throw ReportTruncated("servivano 4 byte, ne restavano ${buffer.remaining()}")
            }
            buffer.position(buffer.position() + 4)
            emptyList()
        }

        // I ventiquattro byte veri del simulatore: sei slot da media e varianza.
        val payload =
                java.util.Base64.getEncoder()
                        .encodeToString(
                                byteArrayOf(
                                        0x27, 0xa5.toByte(), 0x00, 0x01,
                                        0x6e, 0xfb.toByte(), 0xff.toByte(), 0xff.toByte(),
                                        0x05, 0x33, 0x00, 0x76,
                                        0x27, 0xc4.toByte(), 0x00, 0x02,
                                        0x77, 0x28, 0xff.toByte(), 0xff.toByte(),
                                        0x05, 0x74, 0x00, 0x7d,
                                )
                        )

        service.onMeasuresUpdate(
                CuMeasuresUpdate(
                        devEui = 111L,
                        deviceId = "cu-0001001",
                        configVersion = 5,
                        rawPayload = payload,
                        timestamp = null,
                        content = 0x01,
                )
        )

        verify(exactly = 6) { reportDecoder.decodeSlot(any(), any(), any(), any()) }
        assertEquals(0L, cu.decodeFailureCount, "il report si legge per intero, niente da scartare")
    }

    // --- TEST: il taccuino degli eventi del server ---

    @Test
    fun `la topologia cambiata diventa una riga negli eventi`() {
        val cu = ControlUnit().apply {
            id = 5
            devEui = 111L
            deviceId = "lora-e5"
            pollingInterval = 1
        }
        val cambiata = listOf(
                StatusBit(bit = 12, kind = "event", meaning = "topology_changed",
                        description = "Topologia MU cambiata"))

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.statusFlags(null) } returns emptyList()
        every { protocol.statusFlags(0x1000) } returns cambiata
        every { encoder.encodeCuConfig(any(), any(), any(), any()) } returns emptyList()
        every {
            eventRepo.findByControlUnit_IdAndOriginAndCodeAndAcknowledgedAtIsNull(
                    5L, EventOrigin.STATUS, 12)
        } returns emptyList()
        every { eventRepo.save(any()) } answers { firstArg() }

        service.onStatusUpdate(poll(configVersion = 0, statusRaw = 0x1000))

        // Il badge e la scheda «Eventi e diagnostica» guardano la stessa riga: prima il
        // disallineamento viveva come stato corrente e li' non compariva mai.
        verify(exactly = 1) {
            eventRepo.save(match<DeviceEvent> {
                it.origin == EventOrigin.STATUS && it.code == 12
            })
        }
    }

    @Test
    fun `il disallineamento e' un evento solo, non uno per poll`() {
        // realign viene chiamata a ogni poll finche' i conti non tornano, cioe' una volta al
        // minuto: senza la regola dell'evento aperto, la scheda diventerebbe illeggibile in
        // un'ora.
        val cu = ControlUnit().apply {
            id = 5
            devEui = 111L
            deviceId = "lora-e5"
            configVersion = 7
            cmdSeq = 4
        }
        val aperto = DeviceEvent(
                id = 1,
                controlUnit = cu,
                origin = EventOrigin.SERVER,
                code = ServerEventCode.CONFIG_NOT_APPLIED,
        )

        every { cur.findByDevEui(111L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.statusFlags(any()) } returns emptyList()
        every { eventRepo.save(any()) } answers { firstArg() }
        every {
            eventRepo.findByControlUnit_IdAndOriginAndCodeAndAcknowledgedAtIsNull(
                    5L, EventOrigin.SERVER, ServerEventCode.CONFIG_NOT_APPLIED)
        } returnsMany listOf(emptyList(), listOf(aperto))

        service.onStatusUpdate(poll(configVersion = 5, appliedCmdSeq = 3))
        service.onStatusUpdate(poll(configVersion = 5, appliedCmdSeq = 3))

        verify(exactly = 1) { eventRepo.save(any<DeviceEvent>()) }
    }

    @Test
    fun `la presa in carico non cancella la riga, la marca`() {
        val cu = ControlUnit().apply { id = 5; devEui = 111L; deviceId = "lora-e5" }
        val evento = DeviceEvent(
                id = 9,
                controlUnit = cu,
                origin = EventOrigin.STATUS,
                code = 12,
                description = "Topologia MU cambiata",
        )

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(5L) } returns cu
        every { eventRepo.findByIdOrNull(9L) } returns evento
        every { eventRepo.save(any()) } answers { firstArg() }

        val dto = service.acknowledgeEvent(5L, 9L)

        assertTrue(dto.acknowledged, "l'avviso sparisce dagli aperti")
        assertNotNull(evento.acknowledgedAt, "ma la riga resta, con l'istante in cui e' stata vista")
    }

    // --- TEST: configurazione dei sensori ---

    /** Una CU con una MU da quattro slot, pronta a ricevere una configurazione. */
    private fun cuConfigurabile() =
            ControlUnit().apply {
                id = 42
                devEui = 111L
                deviceId = "cu-0001001"
                name = "Marco's CU"
                pollingInterval = 1
                transmissionInterval = 1
                measurementUnits = mutableListOf(mu0001(65537L, 1))
            }

    @Test
    fun `la configurazione sensori manda un comando per ogni cosa che cambia`() {
        val cu = cuConfigurabile()
        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { ts.getDocument(any()) } returns null
        // L'allocatore atomico: il numero lo decide il database.
        every { cur.bumpCmdSeq(42L) } returns 1
        every { cur.readCmdSeq(42L) } returnsMany listOf(4, 5)
        every { encoder.encodePeriods(any(), any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(1, 2, 3), FPort.PERIODS))
        every { encoder.encodeStatsAndThresholds(any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(4, 5), FPort.STATS))
        every { kcu.sendDownlink(any(), any()) } returns Unit

        val esito =
                service.applySensorConfig(
                        42L,
                        SensorConfigCommandSetDTO(
                                sampling =
                                        listOf(
                                                SamplingCommandDTO(
                                                        SensorSelectorDTO(kind = "broadcast"),
                                                        periodIndex = 65,
                                                )
                                        ),
                                processing =
                                        listOf(
                                                ProcessingCommandDTO(
                                                        SensorSelectorDTO(kind = "broadcast"),
                                                        measure = "mm",
                                                )
                                        ),
                        ),
                )

        assertEquals(2, esito.commands.size, "periodi sulla 0x21, elaborazione sulla 0x23")
        assertEquals(listOf(FPort.PERIODS, FPort.STATS), esito.commands.map { it.fPort })
        assertEquals(listOf(4, 5), esito.commands.map { it.cmdSeq }, "due comandi, due CMD_SEQ distinti")
        // Il CFG_VER conta i comandi che la CU applichera', non le richieste HTTP.
        assertEquals(2L, cu.configVersion)
        verify(exactly = 2) { kcu.sendDownlink("cu-0001001", any()) }
        // Lo stato desiderato resta scritto anche se la consegna dovesse fallire.
        assertEquals(65.0, cu.measurementUnits.first().sensors.first().samplingF)
        assertEquals("max-min", cu.measurementUnits.first().sensors.first().configurationMeasure)
    }

    @Test
    fun `una configurazione che non cambia niente non occupa l'aria`() {
        val cu = cuConfigurabile()
        cu.measurementUnits.flatMap { it.sensors }.forEach { it.samplingF = 65.0 }

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { ts.getDocument(any()) } returns null

        val esito =
                service.applySensorConfig(
                        42L,
                        SensorConfigCommandSetDTO(
                                sampling =
                                        listOf(
                                                SamplingCommandDTO(
                                                        SensorSelectorDTO(kind = "broadcast"),
                                                        periodIndex = 65,
                                                )
                                        )
                        ),
                )

        assertTrue(esito.commands.isEmpty())
        assertEquals(0L, cu.configVersion, "nessun comando, nessun incremento da aspettarsi")
        verify(exactly = 0) { kcu.sendDownlink(any(), any()) }
        verify(exactly = 0) { cur.bumpCmdSeq(any()) }
    }

    @Test
    fun `senza periodo di trasmissione la 0x21 non parte`() {
        // Il CU Trans. T viaggia nel blocco 0 come valore assoluto, e zero vale OFF: mandarla
        // direbbe al dispositivo di campionare e poi tacere. Meglio rifiutare e dirlo.
        val cu = cuConfigurabile().apply { transmissionInterval = 0 }

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null

        assertThrows<OperationNotAllowed> {
            service.applySensorConfig(
                    42L,
                    SensorConfigCommandSetDTO(
                            sampling =
                                    listOf(
                                            SamplingCommandDTO(
                                                    SensorSelectorDTO(kind = "broadcast"),
                                                    periodIndex = 65,
                                            )
                                    )
                    ),
            )
        }

        verify(exactly = 0) { kcu.sendDownlink(any(), any()) }
        verify(exactly = 0) { cur.bumpCmdSeq(any()) }
    }

    @Test
    fun `senza periodo di trasmissione le sole funzioni si configurano lo stesso`() {
        // La 0x23 non porta il periodo di trasmissione: non c'e' motivo di fermarla.
        val cu = cuConfigurabile().apply { transmissionInterval = 0 }

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { ts.getDocument(any()) } returns null
        every { cur.bumpCmdSeq(42L) } returns 1
        every { cur.readCmdSeq(42L) } returns 8
        every { encoder.encodeStatsAndThresholds(any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(1), FPort.STATS))
        every { kcu.sendDownlink(any(), any()) } returns Unit

        val esito =
                service.applySensorConfig(
                        42L,
                        SensorConfigCommandSetDTO(
                                processing =
                                        listOf(
                                                ProcessingCommandDTO(
                                                        SensorSelectorDTO(kind = "broadcast"),
                                                        measure = "mm",
                                                )
                                        )
                        ),
                )

        assertEquals(listOf(FPort.STATS), esito.commands.map { it.fPort })
    }

    @Test
    fun `il periodo di trasmissione arriva con la configurazione, senza comandi in piu'`() {
        // Il cursore in cima alla pagina non viaggiava: viveva nel browser finche' non si
        // premeva «Applica», e la configurazione leggeva lo zero sul database. Adesso parte
        // con la richiesta, e la 0x21 lo porta nel blocco 0 — un downlink, non due.
        val cu = cuConfigurabile().apply { transmissionInterval = 0 }

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { protocol.transmissionPeriodMinutes(1) } returns 15L
        every { ts.getDocument(any()) } returns null
        every { cur.bumpCmdSeq(42L) } returns 1
        every { cur.readCmdSeq(42L) } returns 7
        every { encoder.encodePeriods(any(), any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(1, 2, 3), FPort.PERIODS))
        every { kcu.sendDownlink(any(), any()) } returns Unit

        val esito =
                service.applySensorConfig(
                        42L,
                        SensorConfigCommandSetDTO(
                                transmissionIndex = 1,
                                sampling =
                                        listOf(
                                                SamplingCommandDTO(
                                                        SensorSelectorDTO(kind = "broadcast"),
                                                        periodIndex = 65,
                                                )
                                        ),
                        ),
                )

        assertEquals(1, cu.transmissionInterval, "il periodo scelto resta scritto")
        assertEquals(
                listOf(FPort.PERIODS),
                esito.commands.map { it.fPort },
                "una sola 0x21, nessuna 0x24 per il solo periodo",
        )
        // Il valore che viaggia e' quello appena scelto, non lo zero di partenza.
        verify { encoder.encodePeriods(any(), 7, 1) }
    }

    @Test
    fun `dalla configurazione dei sensori non si ferma una CU`() {
        val cu = cuConfigurabile()

        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { ts.getDocument(any()) } returns null

        val esito =
                service.applySensorConfig(42L, SensorConfigCommandSetDTO(transmissionIndex = 0))

        assertEquals(1, cu.transmissionInterval, "lo zero non si applica da qui")
        assertTrue(esito.commands.isEmpty())
        assertTrue(esito.warnings.any { it.contains("non trasmettere") })
        verify(exactly = 0) { kcu.sendDownlink(any(), any()) }
    }

    @Test
    fun `il CMD_SEQ lo assegna il database, non la copia in memoria`() {
        // Due richieste parallele leggevano lo stesso numero e lo usavano entrambe: la CU
        // riconosceva la seconda come ritrasmissione e la scartava. Ora il numero arriva da
        // un'istruzione sola, e la copia in memoria si limita a seguirlo.
        val cu = cuConfigurabile()
        every { ss.isAdmin() } returns true
        every { cur.findByIdOrNull(42L) } returns cu
        every { cur.save(any()) } answers { firstArg() }
        every { protocol.dictionary(any()) } returns null
        every { ts.getDocument(any()) } returns null
        every { cur.bumpCmdSeq(42L) } returns 1
        every { cur.readCmdSeq(42L) } returns 200
        every { encoder.encodePeriods(any(), any(), any()) } returns
                listOf(EncodedPayload(byteArrayOf(1), FPort.PERIODS))
        every { kcu.sendDownlink(any(), any()) } returns Unit

        service.applySensorConfig(
                42L,
                SensorConfigCommandSetDTO(
                        sampling =
                                listOf(
                                        SamplingCommandDTO(
                                                SensorSelectorDTO(kind = "broadcast"),
                                                periodIndex = 65,
                                        )
                                )
                ),
        )

        assertEquals(200, cu.cmdSeq)
        verify { encoder.encodePeriods(any(), 200, 1) }
    }

    private fun poll(configVersion: Int, appliedCmdSeq: Int? = null, statusRaw: Int = 0) =
            CuStatusUpdate(
                    devEui = 111L,
                    deviceId = "lora-e5",
                    model = 1,
                    batteryLevel = 100,
                    ptx = 14,
                    acPowered = true,
                    isCharging = false,
                    statusRaw = statusRaw,
                    configVersion = configVersion,
                    appliedCmdSeq = appliedCmdSeq,
                    protocolVer = 0x12,
                    alarmSeq = 0,
            )
}




