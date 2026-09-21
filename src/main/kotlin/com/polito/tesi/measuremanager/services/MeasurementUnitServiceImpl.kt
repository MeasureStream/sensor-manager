package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.EventMU
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.toMUCreateDTO
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.kafka.KafkaMuProducer
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.template.SensorMapper
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.transaction.Transactional
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
) : MeasurementUnitService {
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

    @Transactional
    override fun delete(id: Long) {
        val mu = mur.findById(id).get()
        if (!ss.isAdmin()) {
            throw OperationNotAllowed(
                "You can't delete a MeasurementUnit owned by someone else",
            )
        }

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
