package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ControlUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository

@Repository
interface ControlUnitRepository : JpaRepository<ControlUnit, Long>, PagingAndSortingRepository<ControlUnit, Long>{
    fun findAllByNetworkIdAndNode_OwnerId(networkId: Long, ownerId: String) : List<ControlUnit>
    fun findAllByNameAndNode_OwnerId(name:String, ownerId: String) : List<ControlUnit>
    fun findAllByNode_OwnerId(ownerId: String) : List<ControlUnit>
    fun findByIdAndNode_OwnerId(id: Long, ownerId: String) : ControlUnit?

    fun findAllByNetworkIdAndNode_OwnerId(networkId: Long, ownerId: String,pageable:Pageable ) : Page<ControlUnit>
    fun findAllByNameAndNode_OwnerId(name:String, ownerId: String,pageable:Pageable ) : Page<ControlUnit>
    fun findAllByNode_OwnerId(ownerId: String,pageable:Pageable ) : Page<ControlUnit>

    fun findAllByNetworkId(networkId : Long ) : List<ControlUnit>
    fun findAllByName(name:String) : List<ControlUnit>

    fun findByNetworkId(networkId: Long) : ControlUnit?

    fun findAllByNetworkId(networkId : Long, pageable:Pageable ) : Page<ControlUnit>
    fun findAllByName(name:String, pageable:Pageable) : Page<ControlUnit>
}