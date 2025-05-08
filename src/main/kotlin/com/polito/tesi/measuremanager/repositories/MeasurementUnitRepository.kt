package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Node
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository

@Repository
interface MeasurementUnitRepository:JpaRepository<MeasurementUnit, Long>,PagingAndSortingRepository<MeasurementUnit, Long> {

    fun findByIdAndNode_OwnerId(id: Long, ownerId: String): MeasurementUnit?

    fun findAllByNetworkIdAndNode_OwnerId(networkId: Long, ownerId: String) : List<MeasurementUnit>
    fun findAllByNetworkIdAndNode_OwnerId(networkId: Long, ownerId: String, pageable:Pageable): Page<MeasurementUnit>

    fun findAllByNode_OwnerId(ownerId: String) : List<MeasurementUnit>
    fun findAllByNode_OwnerId(ownerId: String, pageable: Pageable) : Page<MeasurementUnit>

    fun findAllByNetworkId(networkId: Long ): List<MeasurementUnit>

    fun findByNetworkIdAndNode_OwnerId(networkId: Long, ownerId: String) : MeasurementUnit?
    fun findByNetworkId(networkId: Long) : MeasurementUnit?

    fun findAllByNetworkId(networkId: Long , pageable:Pageable): Page<MeasurementUnit>


}