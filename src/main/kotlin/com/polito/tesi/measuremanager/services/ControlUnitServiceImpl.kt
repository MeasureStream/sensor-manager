package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.*
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.EventOrigin
import com.polito.tesi.measuremanager.entities.Measurement
import com.polito.tesi.measuremanager.entities.ServerEventCode
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
import com.polito.tesi.measuremanager.sensorconfig.DesiredSensorState
import com.polito.tesi.measuremanager.sensorconfig.SensorConfigPlan
import com.polito.tesi.measuremanager.sensorconfig.SensorConfigPlanner
import com.polito.tesi.measuremanager.template.MuModelService
import com.polito.tesi.measuremanager.uplink.FPort
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
import com.polito.tesi.measuremanager.template.StatusBit
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
 * Bit 0 di `Command1`: chiede alla CU il controllo di sanita' delle MU, cioe' una notifica di
 * topologia 0x11 incondizionata. E' un'azione one-shot, non uno stato: si consuma all'arrivo.
 */
private const val COMMAND1_MU_CHECK = 0x01

/**
 * Periodo di poll usato quando il server non ne conosce ancora uno, per poter comporre una
 * 0x0A su una CU mai configurata. **E' una scorciatoia di prova**, non un default di
 * progetto: la configurazione di un dispositivo andrebbe decisa, non inventata dal server.
 * Sta in `STATO_LAVORI.md` fra le scorciatoie da togliere.
 */
private const val DEFAULT_POLLING_HOURS = 1

/**
 * Indice di campionamento che vale «spento». Un sensore a zero non campiona, quindi non
 * occupa posto nel report: e' la riga dello pseudocodice di decodifica della 0x30 che decide
 * quali slot il server deve aspettarsi.
 */
private const val SAMPLING_OFF = 0

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
        private val planner: SensorConfigPlanner,
        private val serverEvents: ServerEventRecorder,
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

        sendCuConfig(cu)

        log.info(
                "Configurazione CU inviata a {}: poll ogni {} h",
                cu.deviceId,
                command.pollingInterval,
        )
        return command
    }

    /**
     * Chiede alla CU il controllo di sanita' delle MU: bit 0 di `Command1`.
     *
     * La CU risponde **sempre** con una notifica di topologia 0x11, anche se non e' cambiato
     * nulla e anche con zero MU. E' l'unico modo che il server ha di riallineare la topologia
     * quando una MU viene staccata e il dispositivo non se n'e' accorto da solo: senza questa
     * richiesta la MU resta censita finche' qualcuno non preme il pulsante di configurazione.
     */
    @Transactional
    override fun requestMuCheck(controlUnitId: Long): ControlUnitDTO {
        val cu = ownedControlUnit(controlUnitId)
        sendCuConfig(cu, command1 = COMMAND1_MU_CHECK)
        log.info("CU {}: chiesto il controllo di sanita' delle MU", cu.deviceId)
        return cu.toDTO(templateService, protocolService)
    }

    /**
     * Configurazione CU 0x0A: quattro byte di **valori assoluti**.
     *
     * Non porta prologo e non incrementa il CFG_VER, ma proprio perche' i valori sono
     * assoluti va composta con quelli correnti: mandare uno zero dove la CU aveva un valore
     * significa cancellarlo. Vale soprattutto per il periodo di poll, dove **0 e' lo
     * spegnimento definitivo e irreversibile**: se il server non lo conosce, il comando non
     * parte affatto.
     */
    private fun sendCuConfig(cu: ControlUnit, command1: Int = 0) {
        if (cu.pollingInterval <= 0) {
            // SCORCIATOIA DI PROVA — vedi STATO_LAVORI.md, «Scorciatoie da togliere».
            //
            // Zero non si manda: per la documentazione e' lo spegnimento definitivo e
            // irreversibile. Inventare un'ora non e' molto meglio, perche' una richiesta
            // diagnostica finisce per **configurare** la CU con un periodo che nessuno ha
            // scelto. Si accetta per poter provare il controllo di sanita' su CU non ancora
            // configurate, e il valore si registra subito: cosi' succede una volta sola per
            // dispositivo e il modello del server resta uguale a cio' che la CU applica.
            //
            // Oggi non ha effetto sul campo perche' il firmware ignora il campo
            // (CU_DEBUG_FIXED_POLL in cu_config.h tiene il poll a 60 s). Diventa vero il
            // giorno in cui quella define sparisce: e' quello il momento in cui questa
            // scorciatoia va tolta, non prima e non dopo.
            log.warn(
                    "CU devEUI={}: periodo di poll non noto, uso il valore di prova di {} h. " +
                            "E' una scorciatoia: la configurazione andrebbe decisa, non inventata.",
                    cu.devEui,
                    DEFAULT_POLLING_HOURS,
            )
            cu.pollingInterval = DEFAULT_POLLING_HOURS
            cur.save(cu)
        }

        encoder.encodeCuConfig(
                        pollingHours = cu.pollingInterval,
                        setting1 = cu.setting1,
                        command1 = command1,
                )
                .forEach { kcu.sendDownlink(cu.deviceId, it) }
    }

    // Transazionale perche' assegna un CMD_SEQ e incrementa il CFG_VER: i due contatori
    // devono muoversi sotto lo stesso lucchetto, altrimenti due configurazioni in parallelo
    // se li contendono.
    @Transactional
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

    /**
     * Applica la configurazione dei sensori preparata dalla schermata dedicata.
     *
     * E' la via nuova, e sostituisce `sendSensorSamplingUpdate` per tutto cio' che passa
     * dall'interfaccia: quella riceveva solo i periodi, questa riceve anche le funzioni di
     * elaborazione e, un giorno, le soglie.
     *
     * Tre cose la governano.
     *
     * **Si spedisce un comando per ogni cosa che cambia davvero.** I periodi viaggiano sulla
     * 0x21 e l'elaborazione sulla 0x23: se cambiano solo i periodi parte un comando solo, e se
     * non cambia niente non parte niente. Rimandare lo stesso valore non e' innocuo — e' un
     * downlink in coda e un incremento di CFG_VER sulla CU — quindi non si fa.
     *
     * **Il CFG_VER cresce di uno per comando applicato, non per richiesta.** Il contatore
     * conta i comandi che la CU ha applicato: se ne partono due, la CU arrivera' a +2 e il
     * server deve aspettarsi lo stesso numero. L'istantanea si prende dopo ogni incremento,
     * perche' e' indicizzata per CFG_VER e un report in ritardo deve trovare la mappa del
     * momento in cui e' stato prodotto.
     *
     * **Lo stato desiderato si scrive prima di spedire.** Se la consegna fallisce resta nel
     * database cio' che l'utente ha chiesto, e si puo' riprovare; se si scrivesse dopo, un
     * comando partito e non registrato lascerebbe server e dispositivo di idee diverse senza
     * che nessuno se ne accorga.
     */
    @Transactional
    override fun applySensorConfig(
            controlUnitId: Long,
            command: SensorConfigCommandSetDTO,
    ): SensorConfigResultDTO {
        val cu = ownedControlUnit(controlUnitId)
        checkSameDevice(cu, command)

        val plan = planner.plan(cu, command)
        requireTransmissionPeriod(cu, plan)

        val sent = mutableListOf<SentCommandDTO>()

        if (plan.periodsChanged) {
            // samplingF conserva l'indice di scala: e' il numero che viaggia sul filo.
            plan.sensors.forEach { it.target.sensor.samplingF = it.periodIndex.toDouble() }
            sent += sendPeriodsCommand(cu, plan)
            bumpConfigVersion(cu)
        }

        val measures = plan.changedMeasures
        if (measures.isNotEmpty()) {
            measures.forEach { it.target.sensor.configurationMeasure = it.measure }
            sent += sendStatsCommand(cu, measures)
            bumpConfigVersion(cu)
        }

        val warnings =
                if (sent.isEmpty()) {
                    plan.warnings +
                            "Nessuna modifica da applicare: i valori richiesti erano gia' quelli in opera."
                } else {
                    plan.warnings
                }

        if (sent.isEmpty()) {
            log.info("CU {}: configurazione sensori senza modifiche, nessun downlink", cu.deviceId)
        } else {
            log.info(
                    "CU {}: configurazione sensori applicata con {} comando/i, CFG_VER atteso {}",
                    cu.deviceId,
                    sent.size,
                    cu.configVersion,
            )
        }
        warnings.forEach { log.warn("CU {}: {}", cu.deviceId, it) }

        return SensorConfigResultDTO(
                controlUnitId = cu.id,
                deviceId = cu.deviceId,
                commands = sent,
                configVersion = cu.configVersion,
                cmdSeq = cu.cmdSeq,
                warnings = warnings,
        )
    }

    /**
     * Senza un periodo di trasmissione la 0x21 non si manda.
     *
     * Il comando dei periodi trasporta il `CU Trans. T` nel blocco 0, come valore assoluto e
     * non come «lascia com'e'»: sulla scala di trasmissione lo **zero e' OFF**. Spedirla
     * mentre il server non conosce un periodo significherebbe dire alla CU di campionare e
     * poi tacere — una configurazione che si spegne da sola, e nessuno che se ne accorga
     * finche' non mancano i dati.
     *
     * E' la stessa trappola del ΔT_Polling nella 0x0A, con una differenza: li' il valore
     * inventato e' una scorciatoia dichiarata, qui inventarlo vorrebbe dire scegliere ogni
     * quanto un dispositivo occupa l'etere. Quindi si rifiuta, e si dice cosa fare.
     *
     * Il blocco vale solo quando i periodi cambiano davvero: configurare le sole funzioni di
     * elaborazione non passa dalla 0x21 e non ha motivo di essere fermato.
     */
    private fun requireTransmissionPeriod(cu: ControlUnit, plan: SensorConfigPlan) {
        if (!plan.periodsChanged || cu.transmissionInterval > 0) return
        throw OperationNotAllowed(
                "Periodo di trasmissione non configurato per ${cu.deviceId}: il comando dei periodi lo " +
                        "trasporta nel primo blocco e il valore 0 vale «non trasmettere». Imposta prima " +
                        "l'intervallo di trasmissione, poi riprova."
        )
    }

    /**
     * La pagina puo' essere rimasta aperta su un'altra CU. Il DevEUI dichiarato non seleziona
     * niente — il dispositivo lo sceglie il percorso della richiesta — ma se non corrisponde
     * significa che l'utente sta guardando un dispositivo e ne configurerebbe un altro.
     */
    private fun checkSameDevice(cu: ControlUnit, command: SensorConfigCommandSetDTO) {
        val declared = command.devEui?.trim()?.toLongOrNull() ?: return
        if (declared == cu.devEui) return
        throw OperationNotAllowed(
                "La configurazione e' stata preparata per il DevEUI $declared, non per ${cu.deviceId}: ricarica la pagina."
        )
    }

    /** Un comando applicato, un CFG_VER, un'istantanea. */
    private fun bumpConfigVersion(cu: ControlUnit) {
        cu.configVersion++
        cur.save(cu)
        snapshots.take(cu)
    }

    private fun sendPeriodsCommand(cu: ControlUnit, plan: SensorConfigPlan): SentCommandDTO {
        val cmdSeq = nextCmdSeq(cu)
        val payloads = encoder.encodePeriods(periodsCommand(cu, plan), cmdSeq, cu.transmissionInterval)
        payloads.forEach { kcu.sendDownlink(cu.deviceId, it) }

        return SentCommandDTO(
                fPort = FPort.PERIODS,
                cmdSeq = cmdSeq,
                packets = payloads.size,
                bytes = payloads.sumOf { it.bytes.size },
                sensors = plan.sensors.count { it.periodChanged },
        )
    }

    /**
     * La 0x21 elenca il periodo di **tutti** i sensori, non solo di quelli cambiati: e' un
     * comando di stato pieno. Lo stream lo costruisce il piano, che ha gia' fuso le modifiche
     * con quello che c'era.
     */
    private fun periodsCommand(cu: ControlUnit, plan: SensorConfigPlan): CUConfigurationDTO =
            CUConfigurationDTO(
                    devEui = cu.devEui.toString(),
                    configurations =
                            plan.sensors.groupBy { it.target.muLocalId }.map { (localId, states) ->
                                MUConfigCommandDTO(
                                        localId = localId,
                                        sensors =
                                                states.map {
                                                    SensorConfigDTO(
                                                            sensorIndex = it.target.sensorIndex,
                                                            samplingPeriod = it.periodIndex,
                                                    )
                                                },
                                )
                            },
            )

    private fun sendStatsCommand(cu: ControlUnit, measures: List<DesiredSensorState>): SentCommandDTO {
        val cmdSeq = nextCmdSeq(cu)
        val records =
                measures.map {
                    LorawanPayloadEncoder.SensorStatsRecord(
                            globalIndex = it.target.globalIndex,
                            statBitmap = StatBitmap.of(it.measure),
                            // Nessun allarme armato: le soglie non viaggiano finche' manca
                            // l'inversione della taratura, e un bit alzato senza il suo valore
                            // farebbe scattare un allarme su una soglia che nessuno ha scelto.
                            alarmEnable = 0,
                    )
                }

        val payloads = encoder.encodeStatsAndThresholds(cmdSeq, records)
        payloads.forEach { kcu.sendDownlink(cu.deviceId, it) }

        return SentCommandDTO(
                fPort = FPort.STATS,
                cmdSeq = cmdSeq,
                packets = payloads.size,
                bytes = payloads.sumOf { it.bytes.size },
                sensors = measures.size,
        )
    }

    @Transactional
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
        // Lo stato precedente serve prima di sovrascriverlo: il controllo di sanita' delle MU
        // si chiede sul **fronte** di un bit, non finche' resta alzato.
        val previousStatus = cu.statusWord
        cu.statusWord = c.statusRaw
        cu.statusAt = OffsetDateTime.now()

        // ProtoVer: finche' una sola CU non lo dichiara, le vecchie FPort restano accese.
        c.protocolVer?.let { cu.protocolVer = it }

        // CFG_VER nel poll: dice se la configurazione inviata e' stata applicata davvero.
        // E' un'informazione diversa da quella del report - qui non si perde nessuna misura,
        // si scopre che un comando non e' arrivato a destinazione.
        c.appliedCmdSeq?.let { cu.appliedCmdSeq = it }

        c.configVersion?.let { reported ->
            cu.lastReportedConfigVersion = reported
            val expected = (cu.configVersion and 0xFF).toInt()
            if (reported != expected) realign(cu, reported, expected, c.appliedCmdSeq)
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

        // I bit appena comparsi si calcolano una volta sola: servono a due lettori che
        // devono vedere lo stesso fronte — il taccuino degli eventi e la richiesta di sanita'.
        val appeared = appearedFlags(previousStatus, c.statusRaw)
        serverEvents.recordStatusEvents(cu, appeared)
        askMuCheckIfNeeded(cu, appeared)
    }

    /**
     * I bit che compaiono adesso e prima non c'erano.
     *
     * Il confronto e' sui significati e non sui numeri grezzi perche' il dizionario e' la
     * fonte: se un giorno un bit cambia posizione, qui non cambia niente.
     */
    private fun appearedFlags(previous: Int?, current: Int): List<StatusBit> {
        val before = protocolService.statusFlags(previous).map { it.meaning }.toSet()
        return protocolService.statusFlags(current).filter { it.meaning !in before }
    }

    /**
     * Dopo un reset della CU o un cambio di topologia, il server chiede il controllo di
     * sanita' delle MU (§ «Controllo di sanita'» della documentazione).
     *
     * Si chiede **sul fronte**: il bit appena comparso, non il bit alzato. I due bit sono di
     * tipo *evento*, cioe' latchati finche' il server non li conferma — e il protocollo non
     * ha ancora un campo per confermarli. Reagire alla presenza invece che alla comparsa
     * significherebbe un downlink a ogni poll, cioe' uno ogni minuto sulle CU attuali.
     */
    private fun askMuCheckIfNeeded(cu: ControlUnit, appearedFlags: List<StatusBit>) {
        val triggers = setOf("reset", "topology_changed")
        val appeared = appearedFlags.map { it.meaning }.filter { it in triggers }
        if (appeared.isEmpty()) return

        // Senza periodo di poll non si puo' comporre una 0x0A: il comando non parte, ma il
        // motivo va detto, perche' altrimenti la topologia resta disallineata in silenzio.
        if (cu.pollingInterval <= 0) {
            log.warn(
                    "CU devEUI={}: {} ma il periodo di poll non e' noto, controllo di sanita' delle MU non richiesto",
                    cu.devEui,
                    appeared.joinToString(),
            )
            return
        }

        sendCuConfig(cu, command1 = COMMAND1_MU_CHECK)
        log.info(
                "CU devEUI={}: {} -> chiesto il controllo di sanita' delle MU",
                cu.devEui,
                appeared.joinToString(),
        )
    }

    /**
     * L'ultimo contatto, su **qualunque** messaggio arrivi dalla CU.
     *
     * Prima lo scriveva solo il ramo della qualita' del segnale, che e' un canale laterale:
     * una CU che pollava regolarmente ma il cui messaggio di metadati radio si perdeva
     * risultava offline. Il poll e' letteralmente il dispositivo che dice «sono qui», ed era
     * l'unico a non registrarlo.
     *
     * L'istante non torna mai indietro: rileggendo un arretrato di Kafka, i messaggi vecchi
     * non devono far sembrare la CU ferma a ore prima.
     */
    @Transactional
    fun touch(devEui: Long, timestamp: String?) {
        val cu = cur.findByDevEui(devEui) ?: return
        val seen =
                timestamp?.let { runCatching { OffsetDateTime.parse(it).toLocalDateTime() }.getOrNull() }
                        // Come il ramo della qualita' del segnale: l'istante e' UTC, perche'
                        // UTC e' quello che arriva da TTN e quello con cui si confronta.
                        ?: LocalDateTime.now(java.time.ZoneOffset.UTC)
        val known = cu.lastSeen
        if (known != null && known.isAfter(seen)) return
        cu.lastSeen = seen
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

        /*
         * Ogni voce: lo slot da leggere e le metriche che porta. L'ordine e' quello del filo.
         *
         * **I sensori spenti non ci sono.** Lo dice lo pseudocodice di decodifica della 0x30:
         * «per ogni sensore in ordine di indice nel modello di MU, *se il sensore e' attivo
         * (Sampl. T diverso da 0)*». Un sensore con il periodo a zero non campiona e non
         * occupa un byte nel report, quindi contarlo sposta tutte le letture successive di
         * quattro byte e, quando i byte finiscono, il report viene scartato per intero.
         *
         * Con l'istantanea la domanda e' la stessa ma riferita ad allora: conta se lo slot
         * era attivo **in quella configurazione**, non adesso.
         */
        val plan: List<Pair<Sensor, List<String>>> =
                if (snapshot == null) {
                    sortedSensors
                            .filter { (_, sensor) -> sensor.samplingF.toInt() != SAMPLING_OFF }
                            .map { (_, sensor) ->
                                sensor to
                                        reportDecoder.expectedMetrics(
                                                sensor.configurationMeasure,
                                                dto.content,
                                        )
                            }
                } else {
                    val byId = sortedSensors.associate { (_, sensor) -> sensor.id to sensor }
                    snapshot.slots.filter { it["active"] as? Boolean ?: true }.map { slot ->
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
                                    frame,
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

        // Dati senza nessuno slot che li aspetti: il server crede che tutti i sensori siano
        // spenti mentre la CU sta trasmettendo. Leggere zero metriche e dichiarare il report
        // «letto» sarebbe la perdita piu' silenziosa di tutte.
        if (plan.isEmpty() && buffer.remaining() > 0) {
            discardFrame(
                    c,
                    dto,
                    expectedConfigVersion,
                    "Il report porta ${buffer.remaining()} byte di dati ma per il server nessun " +
                            "sensore e' attivo: la configurazione non corrisponde a quella della CU",
                    frame,
            )
            return
        }

        for ((sensor, metrics) in plan) {
            val record = templateService.getDocument(sensor.modelName)
            val template = templateService.getTemplate(sensor.modelName)
            val slotLabel = "slot ${sensor.sensorIndex} (${sensor.modelName})"

            val decoded =
                    try {
                        reportDecoder.decodeSlot(buffer, template, metrics, slotLabel)
                    } catch (e: ReportTruncated) {
                        discardFrame(
                                c,
                                dto,
                                expectedConfigVersion,
                                e.message ?: "Report troncato",
                                frame,
                        )
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
                    frame,
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
     * Il CFG_VER del poll non coincide con quello che il server ha contato: a decidere cosa
     * farne e' il `CMD_SEQ` applicato, non una stima sulla distanza fra i due numeri.
     *
     * `CFG_VER` ha due autori — la CU lo incrementa, il server lo prevede — e due contatori
     * con due autori restano in passo solo finche' ogni comando arriva ed entra in opera una
     * volta sola. `CMD_SEQ` ne ha uno: lo scrive il server nel prologo di blocco e la CU lo
     * restituisce. Confrontarlo non e' una previsione, e' una lettura.
     *
     * * **La CU conferma l'ultimo comando inviato**: la configurazione in opera e' proprio
     *   quella del server, solo numerata diversamente. Si adotta il numero della CU e si
     *   fotografa la mappa corrente, altrimenti ogni report successivo verrebbe scartato per
     *   sempre. Il contatore si muove solo in avanti: tornare indietro farebbe collidere due
     *   istantanee sullo stesso CFG_VER.
     * * **La CU conferma un comando diverso**: un downlink non e' arrivato. La sua
     *   configurazione attiva e' una vecchia, e i suoi report vanno letti con l'istantanea di
     *   allora: non si adotta niente, e il log dice quale comando manca.
     */
    private fun realign(cu: ControlUnit, reported: Int, expected: Int, appliedCmdSeq: Int?) {
        // cmdSeq a 0 significa che il server non ha mai configurato questa CU: non c'e'
        // nessun comando su cui essere d'accordo.
        if (appliedCmdSeq != null && appliedCmdSeq == cu.cmdSeq && cu.cmdSeq != 0) {
            cu.configVersion += (reported - expected) and 0xFF
            cur.save(cu)
            snapshots.take(cu)
            // I conti tornano: se era aperto un avviso «comando non applicato», si chiude da
            // solo. Un avviso che resta quando non vale piu' insegna a ignorare gli avvisi.
            serverEvents.close(cu, EventOrigin.SERVER, ServerEventCode.CONFIG_NOT_APPLIED)
            log.warn(
                    "CU devEUI={}: CFG_VER {} contro i {} contati, ma il comando applicato e' il {} che il server ha inviato: il server si allinea e fotografa la configurazione corrente",
                    cu.devEui,
                    reported,
                    expected,
                    appliedCmdSeq,
            )
            return
        }

        log.warn(
                "CU devEUI={}: il poll dichiara CFG_VER {}, il server attende {}; ultimo comando applicato {} contro {} inviato: la configurazione non e' arrivata a destinazione",
                cu.devEui,
                reported,
                expected,
                appliedCmdSeq ?: "non dichiarato",
                cu.cmdSeq,
        )

        // Finora questo disallineamento viveva solo come confronto fra due numeri: vero
        // adesso, senza storia, e invisibile nella scheda degli eventi. Diventa una riga, una
        // sola finche' resta aperta, che l'utente puo' prendere in carico.
        serverEvents.record(
                cu = cu,
                origin = EventOrigin.SERVER,
                code = ServerEventCode.CONFIG_NOT_APPLIED,
                description =
                        "Configurazione non applicata: la CU dichiara CFG_VER $reported, il server attende $expected " +
                                "(comando applicato ${appliedCmdSeq ?: "non dichiarato"} contro ${cu.cmdSeq} inviato)",
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
    /**
     * @param frame il frame gia' registrato per questo uplink, se c'e'.
     *
     * Il frame si scrive **prima** di leggere le metriche, perche' ogni misura deve poter
     * puntare alla riga da cui arriva. Se poi la lettura fallisce, quella riga va
     * **corretta**, non affiancata da una seconda: due voci per lo stesso uplink — una
     * «Letto» e una «Non decodificabile», allo stesso secondo — raccontano una storia che non
     * e' successa, e fanno sembrare doppio il traffico.
     */
    private fun discardFrame(
            cu: ControlUnit,
            dto: CuMeasuresUpdate,
            expected: Int,
            reason: String,
            frame: UplinkFrame? = null,
    ) {
        cu.decodeFailureCount += 1
        cur.save(cu)
        if (frame != null) {
            frame.status = FrameStatus.DISCARDED_DECODE_ERROR
            frame.failureReason = reason
            frames.save(frame)
        } else {
            saveFrame(cu, dto, expected, FrameStatus.DISCARDED_DECODE_ERROR, reason)
        }
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

    /**
     * Presa in carico di un singolo evento: e' la X sul badge.
     *
     * Non cancella e non nasconde la storia — la riga resta, con l'istante in cui e' stata
     * vista. Toglie solo l'avviso dalla vista di «cosa c'e' di aperto adesso», che e' quello
     * che l'utente chiede quando ha gia' capito e risolto. E se il fatto si ripete, il
     * taccuino ne scrive uno nuovo.
     */
    @Transactional
    override fun acknowledgeEvent(controlUnitId: Long, eventId: Long): DeviceEventDTO {
        ownedControlUnit(controlUnitId)
        val event =
                deviceEvents.findByIdOrNull(eventId)?.takeIf {
                    it.controlUnit?.id == controlUnitId
                }
                        ?: throw EntityNotFoundException(
                                "Evento $eventId non trovato sulla Control Unit $controlUnitId"
                        )

        if (event.acknowledgedAt == null) {
            event.acknowledgedAt = OffsetDateTime.now()
            deviceEvents.save(event)
            log.info("CU {}: evento {} preso in carico", controlUnitId, eventId)
        }
        return event.toDTO()
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
    /**
     * Assegna il prossimo CMD_SEQ.
     *
     * L'incremento lo fa il database con una sola istruzione (vedi
     * [ControlUnitRepository.bumpCmdSeq]), non il giro leggi-incrementa-salva che fino al 23
     * ottobre 2026 permetteva a due richieste parallele di ricevere lo stesso numero. Va
     * chiamata **dentro una transazione**: il lucchetto preso sulla riga dura fino al commit
     * e mette in fila anche tutto quello che viene dopo, CFG_VER compreso.
     */
    private fun nextCmdSeq(cu: ControlUnit): Int {
        cur.bumpCmdSeq(cu.id)
        val allocated = cur.readCmdSeq(cu.id)
        // La copia in memoria si allinea al numero appena scritto: e' lo stesso valore,
        // quindi il salvataggio dell'entita' non puo' riportarlo indietro.
        cu.cmdSeq = allocated
        return allocated
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
