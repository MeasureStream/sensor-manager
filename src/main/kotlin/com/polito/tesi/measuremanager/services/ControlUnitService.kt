package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ControlUnitService {
    fun getControlUnit(id: Long): ControlUnitDTO?

    fun getAllControlUnits(
            name: String?,
    ): List<ControlUnitDTO>

    fun getAllControlUnitsPage(
            page: Pageable,
            name: String?,
    ): Page<ControlUnitDTO>

    fun claimControlUnit(hash: String): ControlUnitDTO

    /** Gli allarmi ricevuti da una CU, dal piu' recente. */
    fun getAlarms(controlUnitId: Long): List<AlarmDTO>

    /** Gli eventi di diagnostica ricevuti da una CU, dal piu' recente. */
    fun getEvents(controlUnitId: Long): List<DeviceEventDTO>

    /** Gli ultimi frame ricevuti da una CU, con l'esito della lettura. */
    fun getFrames(controlUnitId: Long): List<UplinkFrameDTO>

    /**
     * Presa in carico: i contatori tornano a zero e i frame restano, marcati come visti.
     * E' l'utente a decidere quando un problema e' stato guardato, non il server.
     */
    fun acknowledgeFrames(controlUnitId: Long): ControlUnitDTO

    fun onJoinNotification(c: CuJoinNotification)

    fun onStatusUpdate(c: CuStatusUpdate)

    fun onSignalUpdate(dto: SignalQualityUpdate)

    fun update(
            id: Long,
            c: ControlUnitDTO,
    ): ControlUnitDTO

    fun delete(id: Long)

    fun sendPollingUpdate(c: CUConfigCommandDTO): CUConfigCommandDTO?

    fun sendSensorSamplingUpdate(command: CUConfigurationDTO): CUConfigurationDTO

    fun sendTransmissionCommand(command: CUTransmissionCommandDTO): CUTransmissionCommandDTO

    fun updateMetadata(id: Long, newName: String?, newSemanticLocation: String?): ControlUnitDTO

    fun updateMeasureConfig(dto: MeasureCUConfigRequest): MeasureCUConfigRequest

    fun onMeasuresUpdate(dto: CuMeasuresUpdate)
}
