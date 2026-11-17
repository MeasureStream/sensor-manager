package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.DeviceEventDTO
import com.polito.tesi.measuremanager.dtos.EventMU
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitSummaryDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.dtos.toMUCreateDTO
import com.polito.tesi.measuremanager.dtos.toSummaryDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.kafka.KafkaMuProducer
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.SensorAlarmRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.template.ProtocolService
import com.polito.tesi.measuremanager.template.SensorMapper
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service


@Service
class MeasurementUnitServiceImpl(
    private val mur: MeasurementUnitRepository,

    private val kmu: KafkaMuProducer,
    private val sensorMapper: SensorMapper,
    private val templateService: TemplateService,
    private val ss: SecurityService,
    private val protocol: ProtocolService,
    private val deviceEvents: DeviceEventRepository,
    private val alarms: SensorAlarmRepository,
) : MeasurementUnitService {

    private val log = LoggerFactory.getLogger(MeasurementUnitServiceImpl::class.java)
    override fun get(id: Long): MeasurementUnitDTO? {
        val mu =
            if (ss.isAdmin()) {
                mur.findByIdOrNull(id)
            } else {
                mur.findByIdAndUser_UserId(id, ss.getCurrentUserId())
            }

        return mu?.let { sensorMapper.toUnitDTO(it) }
    }

    override fun getAll(
        extendedId: Long?,
        controlUnitDevEUI: Long?,
        controlUnitName: String?,
    ): List<MeasurementUnitDTO> {
        val units =
            when {
                ss.isAdmin() -> extendedId?.let { mur.findAllByExtendedId(it) } ?: mur.findAll()
                else -> {
                    val userId = ss.getCurrentUserId()
                    extendedId?.let { mur.findAllByExtendedIdAndUser_UserId(it, userId) }
                        ?: mur.findAllByUser_UserId(userId)
                }
            }
        return units.map { sensorMapper.toUnitDTO(it) }
    }



    override fun getAllPage(
        page: Pageable,
        extendedId: Long?,
        controlUnitDevEUI: Long?,
        controlUnitName: String?,
    ): Page<MeasurementUnitDTO> {
        if (ss.isAdmin())
            {
                extendedId?.let {
                    return mur.findAllByExtendedId(it, page).map { e-> sensorMapper.toUnitDTO(e) }
                }
                return mur.findAll(page).map { sensorMapper.toUnitDTO(it) }
            }

        extendedId?.let {
            return mur.findAllByExtendedIdAndUser_UserId(it, ss.getCurrentUserId(), page).map {e -> sensorMapper.toUnitDTO(e) }
        }

        return mur.findAllByUser_UserId(ss.getCurrentUserId(), page).map { sensorMapper.toUnitDTO(it) }
    }



    @Transactional
    override fun update(
        id: Long,
        m: MeasurementUnitDTO,
    ): MeasurementUnitDTO {

        TODO("QUI DOVRANNO essere Mandati i comandi")
    }

    override fun summaries(includeArchived: Boolean): List<MeasurementUnitSummaryDTO> {
        val units =
            when {
                ss.isAdmin() -> if (includeArchived) mur.findAll() else mur.findAllByArchivedAtIsNull()
                includeArchived -> mur.findAllByUser_UserId(ss.getCurrentUserId())
                else -> mur.findAllByUser_UserIdAndArchivedAtIsNull(ss.getCurrentUserId())
            }
        if (units.isEmpty()) return emptyList()

        // Due query per tutto l'elenco, non due per riga.
        val ids = units.map { it.id }
        val eventi = deviceEvents.countOpenByMeasurementUnit(ids).associate { it.muId to it.total }
        val allarmi = alarms.countOpenByMeasurementUnit(ids).associate { it.muId to it.total }

        return units
            .map { it.toSummaryDTO(protocol, eventi[it.id] ?: 0, allarmi[it.id] ?: 0) }
            // Prima quelle che hanno qualcosa da dire, poi le piu' recenti: chi apre questa
            // pagina di solito la apre perche' una MU non va.
            .sortedWith(
                compareByDescending<MeasurementUnitSummaryDTO> { it.openAlarms + it.openEvents }
                    .thenByDescending { it.lastSeen }
                    .thenBy { it.label }
            )
    }

    override fun events(id: Long): List<DeviceEventDTO> =
        deviceEvents.findTop500ByMeasurementUnit_IdOrderByReceivedAtDesc(owned(id).id).map { it.toDTO() }

    @Transactional
    override fun dissociate(id: Long): MeasurementUnitSummaryDTO {
        val mu = owned(id)
        // Niente si cancella: la MU esce dall'elenco e basta. Se ricompare in una notifica
        // di topologia torna visibile, perche' a quel punto e' di nuovo in uso.
        mu.archivedAt = OffsetDateTime.now()
        mur.save(mu)
        log.info("MU {} dissociata: fuori dall'elenco, nessun dato cancellato", mu.extendedId)
        return mu.toSummaryDTO(protocol)
    }

    /** La MU, se chi la chiede puo' vederla. */
    private fun owned(id: Long): MeasurementUnit =
        (if (ss.isAdmin()) mur.findByIdOrNull(id) else mur.findByIdAndUser_UserId(id, ss.getCurrentUserId()))
            ?: throw EntityNotFoundException("Measurement Unit $id non trovata")

    /**
     * Cancella tutto: la MU, i suoi sensori e, a cascata sul sensore, le misure.
     *
     * Resta agli amministratori. Non e' una dissociazione piu' energica: `metric_sample` ha
     * `ON DELETE CASCADE` sul sensore, quindi qui se ne va lo storico metrologico di quella
     * MU, che e' esattamente il dato che questa piattaforma esiste per conservare.
     *
     * Eventi e allarmi si cancellano a mano. La chiave esterna li lascerebbe in piedi con il
     * riferimento azzerato — righe che non si sa piu' di chi siano, che e' il modo peggiore
     * di conservare qualcosa.
     */
    @Transactional
    override fun delete(id: Long) {
        val mu = mur.findById(id).get()
        if (!ss.isAdmin()) {
            throw OperationNotAllowed(
                "Solo un amministratore puo' formattare una Measurement Unit: l'operazione " +
                    "cancella anche le misure che ha prodotto.",
            )
        }

        val eventi = deviceEvents.deleteByMeasurementUnit_Id(id)
        val allarmi = alarms.deleteByMeasurementUnit_Id(id)
        log.warn(
            "MU {} formattata: cancellati {} sensori con le loro misure, {} eventi, {} allarmi",
            mu.extendedId,
            mu.sensors.size,
            eventi,
            allarmi,
        )

        val event = mu.toMUCreateDTO()?.let { EventMU(eventType = "DELETE", mu = it) }
        if (event != null) {
            kmu.sendMuCreate(event)
        }

        mur.deleteById(id)
    }


/*
    fun createMuByModel(
        extendedId: Long,
        model: Int,
    ): MeasurementUnit {
        val mu =
            MeasurementUnit().apply {
                this.extendedId = extendedId
                this.model = model
                this.sensors = mutableListOf()
            }

        // Funzione helper interna per aggiungere sensori alla MU
        fun addSensor(
            modelName: String,
            index: Int,
        ) {
            val sensor =
                Sensor(
                    modelName = modelName,
                    measurementUnit = mu,
                    sensorIndex = index,
                )
            mu.sensors.add(sensor)
        }

        when (model) {
            1 -> {
                addSensor("accelerometer_lsm6dsm", 1)
                addSensor("pressure_ms5837", 2)
                addSensor("humidity_hpp845e", 3)
                addSensor("ntc_temperature", 4)
            }
            100 -> {
                addSensor("accelerometer_lsm6dsm", 1)
                addSensor("ntc_temperature", 2)
            }
            else -> throw OperationNotAllowed("Model $model not supported")
        }

        return mu
    }

 */
}





//                    Sensor(measurementUnit = measurementUnit, modelName = "accelerometer_lsm6dsm", sensorIndex = 1),
//                    Sensor(measurementUnit = measurementUnit, modelName = "humidity_hpp845e", sensorIndex = 2),
//                    Sensor(measurementUnit = measurementUnit, modelName = "humidity_hpp845e", sensorIndex = 3),
//                    Sensor(measurementUnit = measurementUnit, modelName = "ntc_temperature", sensorIndex = 4),
