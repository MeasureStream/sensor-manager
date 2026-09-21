package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface MeasurementUnitService {
    fun get(id: Long): MeasurementUnitDTO?

    fun getAll(
        extendedId: Long?,
        controlUnitDevEUI: Long?,
        controlUnitName: String?,
    ): List<MeasurementUnitDTO>

    fun getAllPage(
        page: Pageable,
        extendedId: Long?,
        controlUnitDevEUI: Long?,
        controlUnitName: String?,
    ): Page<MeasurementUnitDTO>



    fun update(
        id: Long,
        m: MeasurementUnitDTO,
    ): MeasurementUnitDTO

    fun delete(id: Long)



}
