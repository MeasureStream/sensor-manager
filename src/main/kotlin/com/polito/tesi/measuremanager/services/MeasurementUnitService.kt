package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable


interface MeasurementUnitService {
    fun get(id:Long):MeasurementUnitDTO?
    fun getAll( networkId:Long?, controlUnitNId: Long?, controlUnitName:String?):List<MeasurementUnitDTO>
    fun getAllPage(page: Pageable, networkId:Long?, controlUnitNId: Long?, controlUnitName:String?): Page<MeasurementUnitDTO>
    fun getByNodeId(nodeId:Long): List<MeasurementUnitDTO>
    fun create( m : MeasurementUnitDTO): MeasurementUnitDTO
    fun update(id:Long, m : MeasurementUnitDTO): MeasurementUnitDTO
    fun delete(id:Long)
    fun getAvailable():List<MeasurementUnitDTO>
    fun getFirstAvailableNId():Long

    fun createforUser(m:MeasurementUnitDTO, userId:String): MeasurementUnitDTO
}