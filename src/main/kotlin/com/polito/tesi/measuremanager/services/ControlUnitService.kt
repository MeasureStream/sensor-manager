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

    fun onJoinNotification(c: CuJoinNotification)

    fun onStatusUpdate(c: CuStatusUpdate)

    fun onSignalUpdate(dto : SignalQualityUpdate)


    fun update(
        id: Long,
        c: ControlUnitDTO,
    ): ControlUnitDTO

    fun delete(id: Long)

    fun sendPollingUpdate(c : CUConfigCommandDTO) : CUConfigCommandDTO?

    fun sendSensorSamplingUpdate(command: CUConfigurationDTO) : CUConfigurationDTO

}
