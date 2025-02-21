package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ControlUnitService {
    fun getControlUnit(id:Long):ControlUnitDTO?
    fun getAllControlUnits( networkId:Long?, name: String? ):List<ControlUnitDTO>
    fun getAllControlUnitsPage(page:Pageable, networkId:Long?, name: String? ):Page<ControlUnitDTO>

    fun create(c: ControlUnitDTO):ControlUnitDTO
    fun update(id: Long, c: ControlUnitDTO ):ControlUnitDTO
    fun delete(id: Long)
}