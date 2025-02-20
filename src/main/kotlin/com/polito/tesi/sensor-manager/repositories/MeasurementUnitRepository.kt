package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository

@Repository
interface MeasurementUnitRepository:JpaRepository<MeasurementUnit, Long>,PagingAndSortingRepository<MeasurementUnit, Long> {
    fun findAllByNetworkId(networkId: Long ): List<MeasurementUnit>


    fun findByNetworkId(networkId: Long) : MeasurementUnit?

    fun findAllByNetworkId(networkId: Long , pageable:Pageable): Page<MeasurementUnit>

}