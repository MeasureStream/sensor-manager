package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.*
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.Measurement
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.entities.SignalQuality
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.hmac.NetworkIdEncoder
import com.polito.tesi.measuremanager.kafka.KafkaCuProducer
import com.polito.tesi.measuremanager.kafka.LorawanPayloadEncoder
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.SignalQualityRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.template.MuModelService
import com.polito.tesi.measuremanager.entities.FrameStatus
import com.polito.tesi.measuremanager.entities.UplinkFrame
import com.polito.tesi.measuremanager.entities.Metric
import com.polito.tesi.measuremanager.entities.MetricSample
import com.polito.tesi.measuremanager.repositories.MetricSampleRepository
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.repositories.SensorAlarmRepository
import com.polito.tesi.measuremanager.repositories.UplinkFrameRepository
import com.polito.tesi.measuremanager.template.ConfigSnapshotService
import com.polito.tesi.measuremanager.template.DecodedMetric
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.ReportDecoder
import com.polito.tesi.measuremanager.template.ReportTruncated
import com.polito.tesi.measuremanager.template.StatBitmap
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * Di quanti comandi il server accetta di essere rimasto indietro rispetto alla CU prima di
 * rimettersi in pari da solo. Otto e' un numero scelto, non misurato: abbastanza da coprire
 * una sequenza di comandi non contati, abbastanza poco da non inseguire un CFG_VER che non
 * ha niente a che fare con questa CU.
 */
private const val MAX_CFG_CATCH_UP = 8

@Service
class ControlUnitServiceImpl(
        private val cur: ControlUnitRepository,
        private val mur: MeasurementUnitRepository,
        private val measurementRepository: MeasurementRepository,
        private val sqr: SignalQualityRepository,
        private val ss: SecurityService,
        private val kcu: KafkaCuProducer,
        private val templateService: TemplateService,
        private val muModelService: MuModelService,
        private val protocolService: ProtocolService,
        private val frames: UplinkFrameRepository,
        private val metricSampleRepository: MetricSampleRepository,
        private val reportDecoder: ReportDecoder,
        private val snapshots: ConfigSnapshotService,
        private val alarms: SensorAlarmRepository,
        private val deviceEvents: DeviceEventRepository,
        private val encoder: LorawanPayloadEncoder,
) : ControlUnitService {

    private val log = LoggerFactory.getLogger(ControlUnitServiceImpl::class.java)

    override fun getAllControlUnits(
            name: String?,
    ): List<ControlUnitDTO> {
        if (ss.isAdmin()) {

            name?.let {
                return cur.findAllByName(it).map { e -> e.toDTO(templateService, protocolService) }
            }
            return cur.findAll().map { it.toDTO(templateService, protocolService) }
        }

        val userId = ss.getCurrentUserId()

        name?.let {
            return cur.findAllByNameAndUser_UserId(it, userId).map { e -> e.toDTO(templateService, protocolService) }
        }
        return cur.findAllByUser_UserId(userId).map { it.toDTO(templateService, protocolService) }
    }

    override fun getControlUnit(id: Long): ControlUnitDTO? {
        if (ss.isAdmin()) {
            return cur.findByIdOrNull(id)?.toDTO(templateService, protocolService)
        }
        val userId = ss.getCurrentUserId()
        return cur.findByIdAndUser_UserId(id, userId)?.toDTO(templateService, protocolService)
                ?: throw EntityNotFoundException("ControlUnit $id not found")
    }

    override fun getAllControlUnitsPage(
            page: Pageable,
            name: String?,
    ): Page<ControlUnitDTO> {
        if (ss.isAdmin()) {

            return cur.findAll(page).map { it.toDTO(templateService, protocolService) }
        }
        val userId = ss.getCurrentUserId()

        return cur.findAllByUser_UserId(userId, page).map { it.toDTO(templateService, protocolService) }
    }

    @Transactional
    override fun update(
            id: Long,
            c: ControlUnitDTO,
    ): ControlUnitDTO {
        TODO("QUI DOVRANNO essere Mandati i comandi")
    }

    @Transactional
    override fun updateMetadata(
            id: Long,
            newName: String?,
            newSemanticLocation: String?
    ): ControlUnitDTO {
        val cu =
                cur.findById(id).orElseThrow {
                    EntityNotFoundException("Control Unit con ID $id non trovata")
                }

        if (newName != null) {
            if (newName.isBlank())
                    throw IllegalArgumentException(
                            "Il nome non può essere vuoto o composto da soli spazi"
                    )
            cu.name = newName
        }

        if (newSemanticLocation != null) {
            cu.semanticLocation = newSemanticLocation
        }

        return cur.save(cu).toDTO(templateService, protocolService)
    }

    @Transactional
    override fun claimControlUnit(hash: String): ControlUnitDTO {
        // 1. Decodifica il networkId dall'hash (es. QR code sul dispositivo)
        val decodedDevEui = NetworkIdEncoder.decode(hash)
        val currentUser = ss.getOrCreateCurrentUser()

        val cu =
                cur.findByDevEui(decodedDevEui)
                        ?: throw EntityNotFoundException(
                                "Il dispositivo non è ancora stato censito dalla rete. Accendilo e riprova."
                        )

        if (cu.user != null) {
            throw OperationNotAllowed(
                    "Questa Control Unit è già stata rivendicata da un altro utente."
            )
        }

        cu.user = currentUser
        cu.name = "La mia CU $decodedDevEui"

        return cur.save(cu).toDTO(templateService, protocolService)
    }

    override fun delete(id: Long) {
        if (!ss.isAdmin())
                throw OperationNotAllowed("You can't delete a ControlUnit if you are not an admin")

        val cu = cur.findById(id).get()
        val cuCreateDTO = cu.toCuCreateDTO()

        cur.deleteById(id)
        val event = EventCU(eventType = "DELETE", cu = cuCreateDTO)
        kcu.sendCuCreate(event)
    }

    override fun sendPollingUpdate(command: CUConfigCommandDTO): CUConfigCommandDTO? {
        val cu =
                cur.findByDevEui(command.devEui.toLong())
                        ?: throw EntityNotFoundException("CU non trovata")
        cu.pollingInterval = command.pollingInterval
        cur.save(cu)

        kcu.sendPollingUpdate(command.deviceId, command)

        println("Comando di polling inviato a ${command.deviceId}: ${command.pollingInterval}s")
        return command
    }

    override fun sendSensorSamplingUpdate(command: CUConfigurationDTO): CUConfigurationDTO {
        // 1. Controllo permessi (Security)
        if (!ss.isAdmin()) throw OperationNotAllowed("Non hai i permessi per configurare i sensori")

        // 2. Recupero della Entity CU tramite devEui
        // Assumiamo che devEui nel DB sia memorizzato come Long o String
        val cu =
                cur.findByDevEui(command.devEui.toLong())
                        ?: throw EntityNotFoundException(
                                "Control Unit con DevEui ${command.devEui} non trovata"
                        )

        // 2b. Enforcement del limite MAX_SENSORS_PER_CU (48):
        // ricostruiamo l'insieme dei sensori configurabili con lo stesso ordinamento
        // usato nei DTO (MU per localId, sensori per sensorIndex) e forziamo a OFF (0)
        // il periodo di campionamento di qualunque sensore oltre la soglia,
        // qualunque cosa arrivi dal client.
        val configurableKeys = buildSet {
            var count = 0
            cu.measurementUnits.sortedBy { it.localId }.forEach { mu ->
                mu.sensors.sortedBy { it.sensorIndex }.forEach { s ->
                    if (count < MAX_SENSORS_PER_CU) add(mu.localId to s.sensorIndex)
                    count++
                }
            }
        }
        val safeCommand =
                command.copy(
                        configurations =
                                command.configurations.map { muCfg ->
                                    muCfg.copy(
                                            sensors =
                                                    muCfg.sensors.map { sCfg ->
                                                        if ((muCfg.localId to sCfg.sensorIndex) in
                                                                        configurableKeys
                                                        ) {
                                                            sCfg
                                                        } else {
                                                            println(
                                                                    "Sensore MU=${muCfg.localId}/idx=${sCfg.sensorIndex} oltre il limite di " +
                                                                            "$MAX_SENSORS_PER_CU sensori per CU: campionamento forzato a OFF"
                                                            )
                                                            sCfg.copy(samplingPeriod = 0)
                                                        }
                                                    }
                                    )
                                }
                )

        safeCommand.configurations.forEach { muConfig ->
            val muEntity = cu.measurementUnits.find { it.localId == muConfig.localId }

            muEntity?.let { mu ->
                muConfig.sensors.forEach { sensorConfig ->
                    val sensorEntity =
                            mu.sensors.find { it.sensorIndex == sensorConfig.sensorIndex }

                    sensorEntity?.let { sensor ->
                        sensor.samplingF = sensorConfig.samplingPeriod.toDouble()
                    }
                }
            }
        }

        cur.save(cu)

        // Una configurazione nuova e' una nuova epoca: CFG_VER sale, e l'istantanea registra
        // quali slot erano attivi e con quali metriche. Senza, un report che arriva in ritardo
        // di una configurazione non sarebbe piu' leggibile.
        cu.configVersion++
        cur.save(cu)
        snapshots.take(cu)

        encoder.encodePeriods(safeCommand, nextCmdSeq(cu), cu.transmissionInterval)
                .forEach { kcu.sendDownlink(cu.deviceId, it) }

        return safeCommand
    }

    override fun sendTransmissionCommand(
            command: CUTransmissionCommandDTO
    ): CUTransmissionCommandDTO {
        if (!ss.isAdmin()) throw OperationNotAllowed("Non hai i permessi per configurare i sensori")
        val cu =
                cur.findByDevEui(command.devEui.toLong())
                        ?: throw EntityNotFoundException(
                                "Control Unit con DevEui ${command.devEui} non trovata"
                        )
        cu.transmissionInterval = command.transmissionIndex
        cur.save(cu)

        // La 0x24 non tocca i periodi dei sensori, quindi la mappa degli slot non cambia — ma
        // il CFG_VER si incrementa lo stesso, perche' quel contatore conta **i comandi che la
        // CU ha applicato**, non le versioni dello schema di decodifica. E' la doppia natura
        // che la documentazione elenca fra le questioni aperte.
        //
        // Finche' il server non lo seguiva, bastava una programmazione breve per farlo restare
        // indietro: il primo report successivo dichiarava un CFG_VER che il server non
        // conosceva, e veniva scartato per disallineamento. L'istantanea si prende comunque,
        // anche se il contenuto e' identico alla precedente, perche' e' indicizzata per
        // CFG_VER: senza, quel numero non avrebbe nessuna mappa a cui corrispondere.
        cu.configVersion++
        cur.save(cu)
        snapshots.take(cu)

        encoder.encodeTransmissionConfig(command, nextCmdSeq(cu))
                .forEach { kcu.sendDownlink(cu.deviceId, it) }
        return cu.toCUTransmissionCommandDTO()
    }

    /**
     * Gli slot di una MU vengono dal modello pubblicato nel registro (passo 11): l'elenco dei
     * sensori non e' piu' scritto qui. Un modello sconosciuto non e' piu' un errore: la MU
     * nasce senza slot e li riceve quando il modello viene pubblicato.
     */
    fun createMuByModel(
            extendedId: Long,
            model: Int,
            localId: Int,
            major: Int? = null,
    ): MeasurementUnit = muModelService.createMeasurementUnit(extendedId, model, localId, major)

    @Transactional
    override fun onJoinNotification(c: CuJoinNotification) {
        val cu = getOrCreateControlUnit(c.devEui, c.deviceId)
        val savedCu = cur.save(cu)

        savedCu.measurementUnits.toList().forEach { mu ->
            mu.controlUnit = null
            mur.save(mu)
        }
        savedCu.measurementUnits.clear()

        c.muList.forEach { muDesc ->
            var mu = mur.findByExtendedId(muDesc.extendedId)

            if (mu == null) {
                mu = createMuByModel(muDesc.extendedId, muDesc.model, muDesc.localId, muDesc.major)
            } else {
                // Una MU riprogrammata con un altro MAJOR puo' avere slot diversi. Non li si
                // ricrea: portano configurazione e storico, e buttarli per un byte sarebbe
                // peggio del disallineamento. Si registra la versione nuova e si dice che le
                // due non corrispondono piu'.
                if (muDesc.major != null && mu.modelMajor != null && mu.modelMajor != muDesc.major) {
                    log.warn(
                            "MU {} dichiara il modello 0x{} MAJOR {}, gli slot sono quelli del MAJOR {}: vanno rigenerati a mano",
                            muDesc.extendedId,
                            "%04X".format(muDesc.model),
                            muDesc.major,
                            mu.modelMajor,
                    )
                }
                mu.model = muDesc.model
                mu.localId = muDesc.localId
                if (muDesc.major != null) mu.modelMajor = muDesc.major
                // Una MU rimasta senza slot perche' il modello non era pubblicato li riceve
                // ora, senza aspettare la prossima pubblicazione.
                muModelService.materialize(mu)
            }

            mu.controlUnit = savedCu
            mu.user = savedCu.user

            savedCu.measurementUnits.add(mu)

            mur.save(mu)
        }

        cur.save(savedCu)
    }

    @Transactional
    override fun onStatusUpdate(c: CuStatusUpdate) {
        // 1. Recupera o crea la CU
        val cu = getOrCreateControlUnit(c.devEui, c.deviceId)

        // 2. Aggiorna i parametri dinamici (Byte 5 e 6-7 della tabella)
        // Convertiamo il batteryLevel (0-255 o 0-100) nel Double dell'entità
        cu.remainingBattery = c.batteryLevel.toDouble()
        cu.transmissionPower = c.ptx
        cu.acPowered = c.acPowered
        cu.isCharging = c.isCharging

        cu.model = c.model
        cu.statusWord = c.statusRaw
        cu.statusAt = OffsetDateTime.now()

        // ProtoVer: finche' una sola CU non lo dichiara, le vecchie FPort restano accese.
        c.protocolVer?.let { cu.protocolVer = it }

        // CFG_VER nel poll: dice se la configurazione inviata e' stata applicata davvero.
        // E' un'informazione diversa da quella del report - qui non si perde nessuna misura,
        // si scopre che un comando non e' arrivato a destinazione.
        c.configVersion?.let { reported ->
            cu.lastReportedConfigVersion = reported
            val expected = (cu.configVersion and 0xFF).toInt()
            if (reported != expected) realign(cu, reported, expected, c.statusRaw)
        }

        // ALARM_SEQ nel poll: e' l'unico modo di accorgersi di un allarme che non e' mai
        // arrivato, perche' di quel messaggio non c'e' traccia da nessuna parte.
        c.alarmSeq?.let { reported ->
            cu.reportedAlarmSeq = reported
            val known = cu.lastAlarmSeq
            if (known != null && reported != known) {
                log.warn(
                        "CU devEUI={}: il poll dichiara ALARM_SEQ {}, l'ultimo allarme ricevuto era {}: messaggi di allarme persi",
                        c.devEui,
                        reported,
                        known,
                )
            }
        }

        if (c.statusRaw != 0) {
            log.info(
                    "CU devEUI={}: stato {}",
                    c.devEui,
                    protocolService.statusFlags(c.statusRaw).joinToString { it.description },
            )
        }

        cur.save(cu)
    }

    @Transactional
    override fun onSignalUpdate(dto: SignalQualityUpdate) {
        val devEuiLong =
                try {
                    dto.devEUI.toLong(16)
                } catch (e: Exception) {
                    println("Errore conversione DevEUI: ${dto.devEUI}")
                    return
                }

        val cu =
                cur.findByDevEui(devEuiLong)
                        ?: run {
                            println("Segnale ricevuto per CU non censita: $devEuiLong")
                            return
                        }

        val parsedDataRate =
                try {
                    dto.dataRate.replace("DR", "").toInt()
                } catch (e: Exception) {
                    0
                }

        val airtimeSeconds =
                try {
                    dto.airtime.replace("s", "").toDouble()
                } catch (e: Exception) {
                    0.0
                }

        val timestampOffset =
                try {
                    java.time.OffsetDateTime.parse(dto.time)
                } catch (e: Exception) {
                    val localDt =
                            java.time.LocalDateTime.parse(
                                    dto.time,
                                    java.time.format.DateTimeFormatter.ISO_DATE_TIME
                            )
                    localDt.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                }

        cu.rssi = dto.rssi.toDouble()
        cu.dataRate = parsedDataRate
        cu.lastSeen = timestampOffset.toLocalDateTime()
        cu.spreadingFactor = dto.spreadingFactor
        cu.bandwidth = (dto.bandwidth / 1000)
        cu.lastAirtime = airtimeSeconds
        cu.usedDailyAirtime += (airtimeSeconds * 1000).toLong()

        val previousFCnt = cu.lastFCnt
        if (previousFCnt != null) {
            when {
                dto.fCnt > previousFCnt + 1 ->
                        println(
                                "ATTENZIONE CU ${cu.name}: persi ${dto.fCnt - previousFCnt - 1} uplink (f_cnt $previousFCnt -> ${dto.fCnt})"
                        )
                dto.fCnt <= previousFCnt ->
                        println(
                                "CU ${cu.name}: f_cnt ripartito da ${dto.fCnt} (era $previousFCnt), probabile reset/rejoin"
                        )
            }
        }
        cu.lastFCnt = dto.fCnt

        cur.save(cu)

        val signalQuality =
                SignalQuality(
                        controlUnit = cu,
                        timestamp = timestampOffset,
                        rssi = dto.rssi.toDouble(),
                        snr = dto.snr,
                        dataRate = parsedDataRate,
                        airtime = airtimeSeconds
                )

        sqr.save(signalQuality)

        println(
                "Aggiornato segnale e salvata serie temporale per CU ${cu.name} [EUI: ${dto.devEUI}]: RSSI=${cu.rssi}, SNR=${dto.snr}, DR=${cu.dataRate}, f_cnt=${dto.fCnt}"
        )
    }

    private val MEASURE_TYPES = listOf("avg-std", "integral", "max-min", "puntual")

    @Transactional
    override fun updateMeasureConfig(dto: MeasureCUConfigRequest): MeasureCUConfigRequest {
        // 0. Controlla i permessi (se necessario come negli altri metodi)
        if (!ss.isAdmin()) throw OperationNotAllowed("Non hai i permessi per configurare i sensori")

        val c =
                cur.findById(dto.cuid).orElseThrow {
                    Exception("CU non trovata con ID: ${dto.cuid}")
                }

        // 1. Mappa di ricerca per aggiornare i sensori (extendedId, sensorId)
        val configMap = dto.sensors.associateBy { Pair(it.extendedId, it.sensorId) }

        // 2. Aggiornamento dei sensori nel DB
        c.measurementUnits.forEach { mu ->
            mu.sensors.forEach { sensor ->
                val key = Pair(mu.extendedId, sensor.id)
                configMap[key]?.let { config ->
                    sensor.configurationMeasure = config.configurationMeasure
                }
            }
        }

        // 3. Incremento e salvataggio della nuova configVersion
        c.configVersion++
        cur.save(c)

        // 4. Ordinamento sensori e conversione della stringa nell'indice numerico (0..3)
        val sortedMeasures =
                c.measurementUnits
                        .sortedBy { it.localId }
                        .flatMap { mu -> mu.sensors.sortedBy { it.sensorIndex } }
                        .map { sensor -> MEASURE_TYPES.indexOf(sensor.configurationMeasure) }

        // 5. Istantanea della nuova epoca, poi i record della 0x23
        snapshots.take(c)

        var globalIndex = 0
        val records =
                c.measurementUnits
                        .sortedBy { it.localId }
                        .flatMap { mu -> mu.sensors.sortedBy { it.sensorIndex } }
                        .map { sensor ->
                            LorawanPayloadEncoder.SensorStatsRecord(
                                    globalIndex = globalIndex++,
                                    statBitmap = statBitmapOf(sensor.configurationMeasure),
                                    // Nessun allarme armato finche' le soglie non arrivano dal
                                    // frontend: un bit alzato qui vorrebbe dire una soglia in piu'
                                    // nel payload, e sarebbe una soglia inventata.
                                    alarmEnable = 0,
                            )
                        }

        encoder.encodeStatsAndThresholds(nextCmdSeq(c), records)
                .forEach { kcu.sendDownlink(c.deviceId, it) }

        // 6. Restituisci il risultato
        return dto
    }

    @Transactional
    override fun onMeasuresUpdate(dto: CuMeasuresUpdate) {
        val c =
                cur.findByDevEui(dto.devEui)
                        ?: run {
                            log.warn("Control Unit non trovata per DevEUI={}", dto.devEui)
                            return
                        }

        // Il CFG_VER viaggia su un byte: il confronto si fa sugli otto bit bassi, altrimenti
        // dalla 256esima configurazione in poi nessun report risulterebbe piu' allineato.
        val expectedConfigVersion = (c.configVersion and 0xFF).toInt()

        // Un CFG_VER diverso non significa piu' «illeggibile»: se il server conserva
        // l'istantanea di quella configurazione, il report si legge con la mappa degli slot
        // di allora. Si scarta solo quando quell'epoca non e' nota.
        val snapshot =
                if (dto.configVersion != expectedConfigVersion)
                        snapshots.forWireVersion(c.id, dto.configVersion)
                else null

        if (dto.configVersion != expectedConfigVersion && snapshot == null) {
            // Il report resta scartato: senza sapere quale configurazione era attiva, quei byte
            // non sono decodificabili nemmeno in seguito. Ma non sparisce in silenzio: il
            // contatore dice quante misure si stanno perdendo e da quando, e l'interfaccia lo
            // mostra. Si rientra riallineando la configurazione o resettando la CU.
            c.configMismatchCount += 1
            c.lastConfigMismatchAt = OffsetDateTime.now()
            c.lastReportedConfigVersion = dto.configVersion
            cur.save(c)

            saveFrame(
                    cu = c,
                    dto = dto,
                    expected = expectedConfigVersion,
                    status = FrameStatus.DISCARDED_CFG_MISMATCH,
                    reason =
                            "La CU dichiara CFG_VER ${dto.configVersion}, il server attende " +
                                    "$expectedConfigVersion: la mappa degli slot attiva non e' nota",
            )

            log.warn(
                    "Report scartato per CFG_VER disallineato: CU devEUI={} dichiara {}, il server attende {} (scartati finora: {})",
                    dto.devEui,
                    dto.configVersion,
                    expectedConfigVersion,
                    c.configMismatchCount,
            )
            return
        }

        // Report allineato: se c'era un disallineamento aperto, e' rientrato.
        if (dto.configVersion == expectedConfigVersion && c.configMismatchCount > 0) {
            log.info(
                    "CU devEUI={} di nuovo allineata su CFG_VER {}: {} report erano stati scartati",
                    dto.devEui,
                    expectedConfigVersion,
                    c.configMismatchCount,
            )
            c.configMismatchCount = 0
            c.lastConfigMismatchAt = null
            c.lastReportedConfigVersion = null
            c.decodeFailureCount = 0
            cur.save(c)
        }

        if (dto.rawPayload.isBlank()) {
            discardFrame(c, dto, expectedConfigVersion, "Payload vuoto")
            return
        }

        val bytes =
                try {
                    Base64.getDecoder().decode(dto.rawPayload)
                } catch (e: Exception) {
                    discardFrame(c, dto, expectedConfigVersion, "Base64 non valido: ${e.message}")
                    return
                }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Ordinamento garantito: LocalID della MU, poi SensorIndex
        val sortedSensors =
                c.measurementUnits.sortedBy { it.localId }.flatMap { mu ->
                    mu.sensors.sortedBy { it.sensorIndex }.map { sensor -> Pair(mu, sensor) }
                }

        val timestamp = dto.timestamp?.let { OffsetDateTime.parse(it) } ?: OffsetDateTime.now()

        // Il frame si registra prima delle misure, cosi' ogni misura punta al frame da cui
        // arriva: dal byte ricevuto al valore mostrato c'e' una traccia sola.
        val frame =
                saveFrame(
                        cu = c,
                        dto = dto,
                        expected = expectedConfigVersion,
                        status =
                                if (snapshot == null) FrameStatus.DECODED
                                else FrameStatus.DECODED_WITH_SNAPSHOT,
                        reason =
                                snapshot?.let {
                                    "Letto con l'istantanea della configurazione ${it.cfgVersion}"
                                },
                        // Cosa diceva l'header: senza, un report con meno metriche del
                        // previsto sembrerebbe un errore di configurazione invece che un
                        // ciclo in cui le EXTENDED non erano di turno.
                        decoded =
                                if (dto.content != null || dto.fragments > 1)
                                        mapOf("content" to dto.content, "fragments" to dto.fragments)
                                else null,
                )

        val samples = mutableListOf<MetricSample>()
        var noNewData = 0
        var notResponding = 0

        // Ogni voce: lo slot da leggere e le metriche che porta. L'ordine e' quello del filo.
        val plan: List<Pair<Sensor, List<String>>> =
                if (snapshot == null) {
                    sortedSensors.map { (_, sensor) ->
                        sensor to
                                reportDecoder.expectedMetrics(
                                        sensor.configurationMeasure,
                                        dto.content,
                                )
                    }
                } else {
                    val byId = sortedSensors.associate { (_, sensor) -> sensor.id to sensor }
                    snapshot.slots.map { slot ->
                        val sensorId = (slot["sensorId"] as? Number)?.toLong()
                        val sensor = byId[sensorId]
                        if (sensor == null) {
                            // Uno slot dell'istantanea non esiste piu': da qui in poi ogni
                            // lettura sarebbe attribuita al sensore sbagliato.
                            discardFrame(
                                    c,
                                    dto,
                                    expectedConfigVersion,
                                    "L'istantanea della configurazione ${dto.configVersion} cita il " +
                                            "sensore $sensorId, che non esiste piu'",
                            )
                            return
                        }
                        sensor to
                                reportDecoder.expectedMetrics(
                                        slot["configurationMeasure"] as? String,
                                        dto.content,
                                )
                    }
                }

        if (snapshot != null) {
            log.info(
                    "CU devEUI={}: report letto con l'istantanea della configurazione {} ({} slot)",
                    dto.devEui,
                    snapshot.cfgVersion,
                    snapshot.slots.size,
            )
        }

        for ((sensor, metrics) in plan) {
            val record = templateService.getDocument(sensor.modelName)
            val template = templateService.getTemplate(sensor.modelName)
            val slotLabel = "slot ${sensor.sensorIndex} (${sensor.modelName})"

            val decoded =
                    try {
                        reportDecoder.decodeSlot(buffer, template, metrics, slotLabel)
                    } catch (e: ReportTruncated) {
                        discardFrame(c, dto, expectedConfigVersion, e.message ?: "Report troncato")
                        return
                    }

            decoded.forEach { metric ->
                // Le sentinelle non sono misure: si contano e non si salvano, altrimenti
                // 65535 finirebbe nei grafici come se fosse un valore letto.
                if (metric.notResponding) {
                    notResponding++
                    return@forEach
                }
                if (metric.noNewData) {
                    noNewData++
                    return@forEach
                }
                samples.add(
                        MetricSample(
                                sensor = sensor,
                                timestamp = timestamp,
                                metric = metric.metric,
                                value = metric.value,
                                rawValue = metric.raw,
                                converted = metric.converted,
                                templateVersion = record?.version,
                                uplinkFrame = frame,
                        )
                )
            }

            updateLiveValue(sensor, decoded)
        }

        if (buffer.remaining() > 0) {
            // Byte in piu' rispetto a quelli descritti: la mappa degli slot non corrisponde a
            // quella con cui la CU ha trasmesso, quindi anche cio' che si e' letto e' dubbio.
            discardFrame(
                    c,
                    dto,
                    expectedConfigVersion,
                    "Il report ha ${buffer.remaining()} byte oltre le metriche attese: " +
                            "la configurazione degli slot non corrisponde",
            )
            return
        }

        metricSampleRepository.saveAll(samples)
        log.info(
                "Misure salvate: {} metriche da {} slot{}{}{}",
                samples.size,
                plan.size,
                if (dto.fragments > 1) " (${dto.fragments} frammenti)" else "",
                if (noNewData > 0) ", $noNewData senza dati nuovi" else "",
                if (notResponding > 0) ", $notResponding da sensori muti" else "",
        )
    }

    /**
     * Il CFG_VER del poll non coincide con quello che il server ha contato: si decide se
     * rimettersi in pari o se limitarsi a dirlo.
     *
     * I due contatori sono indipendenti — il server conta i comandi che manda, la CU quelli
     * che applica — e restano in passo solo finche' ogni comando arriva ed entra in opera
     * esattamente una volta. Quando divergono, la direzione dice cose diverse:
     *
     * * **CU indietro**: il server ha inviato comandi che la CU non ha applicato. La sua
     *   configurazione attiva e' una vecchia, e i report vanno letti con l'istantanea di
     *   allora. Non si adotta niente, altrimenti si leggerebbero quei byte con la mappa
     *   sbagliata.
     * * **CU avanti di pochi**: ha applicato qualcosa che il server non ha contato. La
     *   configurazione attiva resta l'ultima che il server ha inviato, quindi il numero si
     *   adotta e si fotografa la mappa corrente con quel numero. Senza, ogni report
     *   successivo verrebbe scartato per sempre: il server aspetterebbe un CFG_VER che la CU
     *   non tornera' mai a dichiarare.
     *
     * Con il bit di reset alzato non si adotta nulla: dopo un riavvio il contatore riparte,
     * ma la CU potrebbe anche aver perso la configurazione, e rimettersi in pari sul numero
     * significherebbe dichiarare allineato cio' che non lo e'.
     */
    private fun realign(cu: ControlUnit, reported: Int, expected: Int, statusRaw: Int) {
        val ahead = (reported - expected) and 0xFF
        val afterReset = protocolService.statusFlags(statusRaw).any { it.meaning == "reset" }

        if (ahead in 1..MAX_CFG_CATCH_UP && !afterReset) {
            cu.configVersion += ahead
            cur.save(cu)
            snapshots.take(cu)
            log.warn(
                    "CU devEUI={}: il poll dichiara CFG_VER {}, il server ne aveva contati {}: il server si allinea e fotografa la configurazione corrente",
                    cu.devEui,
                    reported,
                    expected,
            )
            return
        }

        log.warn(
                "CU devEUI={}: il poll dichiara CFG_VER {}, il server attende {}{}: configurazione non applicata",
                cu.devEui,
                reported,
                expected,
                if (afterReset) " dopo un reset" else "",
        )
    }

    /**
     * L'ultimo valore letto, sul sensore: e' quello che le schede delle MU mostrano.
     *
     * Le misure vivono in `metric_sample`, ma la scheda di una CU ne vuole una sola per
     * sensore, l'ultima, e cercarla ogni volta significherebbe una query per sensore a ogni
     * apertura della pagina. Questi due campi sono quella cache — esistevano gia' e il
     * frontend li legge ancora, ma dopo il passaggio a `metric_sample` nessuno li scriveva
     * piu': da li' gli zeri fissi nelle schede.
     *
     * La media e' la metrica giusta da mostrare; se il sensore non la trasmette si ripiega
     * sul valore puntuale e poi sulla prima metrica letta. Il grezzo si scrive sempre, il
     * valore fisico **solo se la conversione e' avvenuta davvero**: un numero non convertito
     * accanto a «°C» sarebbe una misura inventata.
     */
    private fun updateLiveValue(sensor: Sensor, decoded: List<DecodedMetric>) {
        val usable = decoded.filterNot { it.noNewData || it.notResponding }
        val live =
                usable.firstOrNull { it.metric == Metric.MEAN }
                        ?: usable.firstOrNull { it.metric == Metric.PUNCTUAL }
                                ?: usable.firstOrNull()
                        ?: return

        sensor.elecVal = live.raw
        if (live.converted && live.value != null) sensor.physVal = live.value!!
        // Il sensore e' un'entita' gestita dalla transazione: Hibernate scrive al commit.
    }

    /**
     * Registra un frame che non si e' potuto leggere e conta l'occorrenza.
     *
     * Si scarta tutto il report invece di salvare la parte compresa: se i byte non tornano,
     * anche quelli letti prima potrebbero appartenere a slot diversi da quelli supposti, e
     * una misura attribuita al sensore sbagliato e' peggio di una misura mancante. Il frame
     * grezzo resta qui, quindi la decisione e' reversibile: quando si scopre perche' non
     * tornava, lo si rilegge.
     */
    private fun discardFrame(
            cu: ControlUnit,
            dto: CuMeasuresUpdate,
            expected: Int,
            reason: String,
    ) {
        cu.decodeFailureCount += 1
        cur.save(cu)
        saveFrame(cu, dto, expected, FrameStatus.DISCARDED_DECODE_ERROR, reason)
        log.warn(
                "Report scartato per DevEUI={}: {} (scartati finora: {})",
                dto.devEui,
                reason,
                cu.decodeFailureCount,
        )
    }

    /** Il payload com'e' arrivato, con l'esito della lettura. Si conserva sempre. */
    private fun saveFrame(
            cu: ControlUnit,
            dto: CuMeasuresUpdate,
            expected: Int,
            status: FrameStatus,
            reason: String?,
            decoded: Map<String, Any?>? = null,
    ): UplinkFrame =
            frames.save(
                UplinkFrame(
                        controlUnit = cu,
                        devEui = dto.devEui,
                        receivedAt =
                                dto.timestamp?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                                        ?: OffsetDateTime.now(),
                        fport = dto.fport,
                        cfgVersion = dto.configVersion,
                        expectedCfgVersion = expected,
                        rawPayload = dto.rawPayload,
                        decoded = decoded,
                        status = status,
                        failureReason = reason,
                    )
            )

    /**
     * Un messaggio che non si e' potuto leggere prima ancora di arrivare alle metriche:
     * frammenti che non si ricompongono, header illeggibile, porta dismessa. Vale la stessa
     * regola dei report scartati - il grezzo resta, il contatore lo dichiara - perche' un
     * messaggio perso in silenzio e' esattamente cio' che il registro dei frame evita.
     */
    @Transactional
    fun recordUndecodableFrame(
            devEui: Long,
            fport: Int,
            rawPayload: String,
            reason: String,
            cfgVersion: Int? = null,
            timestamp: String? = null,
    ) {
        val cu = cur.findByDevEui(devEui)
        if (cu == null) {
            log.warn("Control Unit non trovata per DevEUI={}: {} non registrato", devEui, reason)
            return
        }
        cu.decodeFailureCount += 1
        cur.save(cu)
        frames.save(
                UplinkFrame(
                        controlUnit = cu,
                        devEui = devEui,
                        receivedAt =
                                timestamp?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                                        ?: OffsetDateTime.now(),
                        fport = fport,
                        cfgVersion = cfgVersion,
                        expectedCfgVersion = (cu.configVersion and 0xFF).toInt(),
                        rawPayload = rawPayload,
                        status = FrameStatus.DISCARDED_DECODE_ERROR,
                        failureReason = reason,
                )
        )
        log.warn("DevEUI={}: {} (scartati finora: {})", devEui, reason, cu.decodeFailureCount)
    }
    override fun getAlarms(controlUnitId: Long): List<AlarmDTO> {
        ownedControlUnit(controlUnitId)
        return alarms.findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId).map { it.toDTO() }
    }

    override fun getEvents(controlUnitId: Long): List<DeviceEventDTO> {
        ownedControlUnit(controlUnitId)
        return deviceEvents.findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId).map {
            it.toDTO()
        }
    }

    override fun getFrames(controlUnitId: Long): List<UplinkFrameDTO> {
        ownedControlUnit(controlUnitId)
        return frames.findTop50ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId).map { it.toDTO() }
    }

    @Transactional
    override fun acknowledgeFrames(controlUnitId: Long): ControlUnitDTO {
        val cu = ownedControlUnit(controlUnitId)
        val now = OffsetDateTime.now()
        val marked =
                frames.acknowledgeAll(controlUnitId, now) +
                        alarms.acknowledgeAll(controlUnitId, now) +
                        deviceEvents.acknowledgeAll(controlUnitId, now)

        cu.configMismatchCount = 0
        cu.decodeFailureCount = 0
        cu.lastConfigMismatchAt = null
        cu.lastReportedConfigVersion = null

        log.info(
                "CU {}: presa in carico di {} fra frame, allarmi ed eventi; contatori azzerati",
                controlUnitId,
                marked,
        )
        return cur.save(cu).toDTO(templateService, protocolService)
    }

    /**
     * Il numero del prossimo comando di configurazione. Viaggia su un byte nel prologo di
     * blocco e va in wrap a 255: la CU lo confronta per riconoscere una ritrasmissione.
     */
    private fun nextCmdSeq(cu: ControlUnit): Int {
        cu.cmdSeq = (cu.cmdSeq + 1) and 0xFF
        cur.save(cu)
        return cu.cmdSeq
    }

    /** La `Stat bitmap` della 0x23. La traduzione sta in [StatBitmap], non qui. */
    private fun statBitmapOf(configurationMeasure: String?): Int =
            StatBitmap.of(configurationMeasure)

    /** La CU se chi chiede puo' vederla: un utente vede solo le proprie. */
    private fun ownedControlUnit(id: Long): ControlUnit {
        val cu =
                if (ss.isAdmin()) cur.findByIdOrNull(id)
                else cur.findByIdAndUser_UserId(id, ss.getCurrentUserId())
        return cu ?: throw EntityNotFoundException("Control Unit $id non trovata")
    }

    /**
     * Funzione di supporto per garantire l'idempotenza: Se la CU esiste la restituisce, altrimenti
     * ne crea una "orfana" pronta per il claim.
     */
    fun getOrCreateControlUnit(devEui: Long, deviceId: String): ControlUnit {
        return cur.findByDevEui(devEui)
                ?: ControlUnit().apply {
                    this.devEui = devEui
                    // Generiamo il networkId (l'hash per il QR/Claim) partendo dal devEui
                    this.deviceId = deviceId

                    this.name = "New Device (${devEui})"
                    this.user = null // Rimane null finché l'utente non fa il claim
                    this.remainingBattery = 100.0
                }
    }
}
