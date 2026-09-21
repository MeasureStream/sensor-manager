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
import com.polito.tesi.measuremanager.template.TemplateService
import com.polito.tesi.measuremanager.utils.SensorDecoder
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

@Service
class ControlUnitServiceImpl(
        private val cur: ControlUnitRepository,
        private val mur: MeasurementUnitRepository,
        private val measurementRepository: MeasurementRepository,
        private val sqr: SignalQualityRepository,
        private val ss: SecurityService,
        private val kcu: KafkaCuProducer,
        private val templateService: TemplateService,
        private val encoder: LorawanPayloadEncoder,
) : ControlUnitService {

    private val log = LoggerFactory.getLogger(ControlUnitServiceImpl::class.java)

    override fun getAllControlUnits(
            name: String?,
    ): List<ControlUnitDTO> {
        if (ss.isAdmin()) {

            name?.let {
                return cur.findAllByName(it).map { e -> e.toDTO(templateService) }
            }
            return cur.findAll().map { it.toDTO(templateService) }
        }

        val userId = ss.getCurrentUserId()

        name?.let {
            return cur.findAllByNameAndUser_UserId(it, userId).map { e -> e.toDTO(templateService) }
        }
        return cur.findAllByUser_UserId(userId).map { it.toDTO(templateService) }
    }

    override fun getControlUnit(id: Long): ControlUnitDTO? {
        if (ss.isAdmin()) {
            return cur.findByIdOrNull(id)?.toDTO(templateService)
        }
        val userId = ss.getCurrentUserId()
        return cur.findByIdAndUser_UserId(id, userId)?.toDTO(templateService)
                ?: throw EntityNotFoundException("ControlUnit $id not found")
    }

    override fun getAllControlUnitsPage(
            page: Pageable,
            name: String?,
    ): Page<ControlUnitDTO> {
        if (ss.isAdmin()) {

            return cur.findAll(page).map { it.toDTO(templateService) }
        }
        val userId = ss.getCurrentUserId()

        return cur.findAllByUser_UserId(userId, page).map { it.toDTO(templateService) }
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

        return cur.save(cu).toDTO(templateService)
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

        return cur.save(cu).toDTO(templateService)
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

        val encoded = encoder.encodeSensorConfig(safeCommand)

        kcu.sendDownlink(cu.deviceId, encoded)

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

        val encoded = encoder.encodeTransmissionConfig(command)
        kcu.sendDownlink(cu.deviceId, encoded)
        cur.save(cu)
        return cu.toCUTransmissionCommandDTO()
    }

    fun createMuByModel(extendedId: Long, model: Int, localId: Int): MeasurementUnit {
        val mu =
                MeasurementUnit().apply {
                    this.extendedId = extendedId
                    this.model = model
                    this.sensors = mutableListOf()
                    this.localId = localId
                }

        fun addSensor(
                modelName: String,
                index: Int,
        ) {
            val sensor =
                    Sensor(
                            modelName = modelName,
                            measurementUnit = mu,
                            sensorIndex = index,
                            configurationMeasure = "average-std"
                    )
            mu.sensors.add(sensor)
        }

        when (model) {
            1 -> {
                addSensor("AccelerometerLSM6DSM", 1)
                addSensor("PressureSensorMS5837", 2)
                addSensor("HumiditySensorHTU21D", 3)
                addSensor("TemperatureSensorNTC", 4)
            }
            100 -> {
                addSensor("AccelerometerLSM6DSM", 1)
                addSensor("TemperatureSensorNTC", 2)
            }
            else -> throw OperationNotAllowed("Model $model not supported")
        }

        return mu
    }

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
                mu = createMuByModel(muDesc.extendedId, muDesc.model, muDesc.localId)
            } else {
                mu.model = muDesc.model
                mu.localId = muDesc.localId
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

        // Se hai un campo per lo stato grezzo o per il modello della CU
        // cu.statusRaw = c.statusRaw
        cu.model = c.model

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

        // 5. Invia il comando via LoRaWAN / Kafka
        val encodedPayload = encoder.encodeMeasureConfig(c.configVersion, sortedMeasures)
        kcu.sendDownlink(c.deviceId, encodedPayload)

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

        if (dto.configVersion.toLong() != c.configVersion) {
            log.warn(
                    "Attenzione: arrivata configVersion differente per CU devEUI={}. Ricevuta={}, Attesa={}",
                    dto.devEui,
                    dto.configVersion,
                    c.configVersion
            )
            return
        }

        if (dto.rawPayload.isBlank()) {
            log.warn("Payload vuoto ricevuto per DevEUI={}", dto.devEui)
            return
        }

        val bytes =
                try {
                    Base64.getDecoder().decode(dto.rawPayload)
                } catch (e: Exception) {
                    log.error("Errore decodifica Base64 per DevEUI={}: {}", dto.devEui, e.message)
                    return
                }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Ordinamento garantito: LocalID della MU, poi SensorIndex
        val sortedSensors =
                c.measurementUnits.sortedBy { it.localId }.flatMap { mu ->
                    mu.sensors.sortedBy { it.sensorIndex }.map { sensor -> Pair(mu, sensor) }
                }

        val decodedMeasures = mutableListOf<Map<String, Any?>>()

        for ((mu, sensor) in sortedSensors) {
            // Fallback di sicurezza su configurationMeasure
            val configType = sensor.configurationMeasure?.lowercase() ?: "average-std"

            // Calcolo byte richiesti in base a configType
            val bytesRequired =
                    when (configType) {
                        "avg-std", "average-std", "max-min" -> 4
                        "integral", "puntual" -> 2
                        else -> 2
                    }

            if (buffer.remaining() < bytesRequired) {
                log.warn(
                        "Payload incompleto per DevEUI={}. Byte richiesti: {}, rimasti: {}",
                        dto.devEui,
                        bytesRequired,
                        buffer.remaining()
                )
                break
            }

            val measureData =
                    mutableMapOf<String, Any?>(
                            "muLocalId" to mu.localId,
                            "muExtendedId" to mu.extendedId,
                            "sensorId" to sensor.id,
                            "sensorIndex" to sensor.sensorIndex,
                            "modelName" to sensor.modelName,
                            "configType" to configType,
                            "sensorEntity" to sensor
                    )

            when (configType) {
                "avg-std", "average-std" -> {
                    // In Kotlin/Java .short da signed value; con & 0xFFFF lo rendiamo uint16 puro
                    // (0..65535)
                    val rawMean = buffer.short.toInt() and 0xFFFF
                    val rawVar = buffer.short.toInt() and 0xFFFF

                    // DECODIFICA FISICA TRAMITE SENSOR DECODER
                    val decoded = SensorDecoder.decode(sensor.modelName, rawMean, rawVar)

                    measureData["status"] = decoded.status
                    measureData["rawMean"] = rawMean
                    measureData["rawVar"] = rawVar
                    measureData["physicalValue"] = decoded.physicalValue
                    measureData["physicalVariance"] = decoded.physicalVariance

                    log.debug(
                            "Sensore [MU:{}, Model:{}] -> Status: {}, PhysVal: {}, PhysVar: {}",
                            mu.localId,
                            sensor.modelName,
                            decoded.status,
                            decoded.physicalValue,
                            decoded.physicalVariance
                    )
                }
                "max-min" -> {
                    val rawMax = buffer.short.toInt() and 0xFFFF
                    val rawMin = buffer.short.toInt() and 0xFFFF
                    measureData["rawMax"] = rawMax
                    measureData["rawMin"] = rawMin
                }
                "integral", "puntual" -> {
                    val rawVal = buffer.short.toInt() and 0xFFFF
                    measureData["rawValue"] = rawVal
                }
            }

            decodedMeasures.add(measureData)
        }

        log.info("Decodificate {} misure per DevEUI={}", decodedMeasures.size, dto.devEui)

        val measurementsToSave =
                decodedMeasures.mapNotNull { data ->
                    val sensor = data["sensorEntity"] as? Sensor ?: return@mapNotNull null
                    val configType = data["configType"] as? String ?: return@mapNotNull null

                    val timestamp =
                            dto.timestamp?.let { OffsetDateTime.parse(it) } ?: OffsetDateTime.now()

                    var primary: Double? = null
                    var secondary: Double? = null

                    when (configType) {
                        "avg-std", "average-std" -> {
                            primary = (data["physicalValue"] as? Number)?.toDouble()
                            secondary = (data["physicalVariance"] as? Number)?.toDouble()
                        }
                        "max-min" -> {
                            primary = (data["rawMax"] as? Number)?.toDouble()
                            secondary = (data["rawMin"] as? Number)?.toDouble()
                        }
                        "integral", "puntual" -> {
                            primary = (data["rawValue"] as? Number)?.toDouble()
                        }
                    }

                    Measurement(
                            timestamp = timestamp,
                            sensor = sensor,
                            measurementType = configType, // <-- Assegnato qui
                            valuePrimary = primary,
                            valueSecondary = secondary
                    )
                }

        measurementRepository.saveAll(measurementsToSave)
        log.info("Misure salvate nel DB")
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
