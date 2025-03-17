package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull
@Service
class ControlUnitServiceImpl(private val cur: ControlUnitRepository, private val nr: NodeRepository ):ControlUnitService {
    override fun getAllControlUnits(networkId: Long?, name: String?): List<ControlUnitDTO> {

        networkId?.let {
            return cur.findAllByNetworkId(it).map { e-> e.toDTO() }
        }
        name?.let {
            return cur.findAllByName(it).map { e-> e.toDTO() }
        }
        return cur.findAll().map { it.toDTO() }
    }

    override fun getControlUnit(id: Long): ControlUnitDTO? {
        return cur.findByIdOrNull(id)?.toDTO()
    }



    override fun getAllControlUnitsPage(page: Pageable, networkId: Long?, name: String?): Page<ControlUnitDTO> {

        networkId?.let {
            return cur.findAllByNetworkId(it,page).map { e-> e.toDTO() }
        }
        name?.let {
            return cur.findAllByName(it,page).map { e-> e.toDTO() }
        }
        return cur.findAll(page).map { it.toDTO() }
    }

    override fun getByNodeId(nodeId: Long): List<ControlUnitDTO> {
        val n = nr.findById(nodeId).get()
        return n.controlUnits.toList().map { it.toDTO() }
    }

    @Transactional
    override fun create(c: ControlUnitDTO): ControlUnitDTO {
        if(cur.findByNetworkId(c.networkId) != null ) throw EntityExistsException()//Exception("CU_NetworkID : ${c.networkId} already present")
        if( c.remainingBattery > 100.0 || c.remainingBattery<0.0 ) throw OperationNotAllowed("remain battery out of range c.remainingBattery: ${c.remainingBattery}")
        if( c.nodeId != null && nr.findById(c.nodeId).isEmpty ) throw EntityNotFoundException("Node ${c.nodeId} not exists ")

        val n = if (c.nodeId != null ) nr.findById(c.nodeId).get() else null

        val cu = ControlUnit().apply {
            networkId = c.networkId
            name = c.name
            remainingBattery = c.remainingBattery
            rssi = c.rssi
            node = n

        }
        val savedC = cur.save(cu)
        if (n != null) {
            n.controlUnits.add(savedC)
            nr.save(n)
        }


        return savedC.toDTO()
    }
    @Transactional
    override fun update(id: Long, c: ControlUnitDTO): ControlUnitDTO {
        if( id != c.id ) throw Exception("can't update")
        val cu = cur.findById(id).get()
        if( cu.networkId != c.networkId ) throw EntityNotFoundException()

        val controlUnit = cu.apply {
            name = c.name
            remainingBattery = c.remainingBattery
            rssi = c.rssi
        }

        when {
            cu.node == null && c.nodeId == null -> {
                cur.save(controlUnit)
                return cu.toDTO()
            }
            cu.node == null && c.nodeId != null -> {
                val newNode = nr.findById(c.nodeId).get()
                controlUnit.node = newNode
                val newC = cur.save(controlUnit)
                newNode.controlUnits.add(newC)
                nr.save(newNode)
                return newC.toDTO()
            }
            cu.node != null  && c.nodeId == null -> {
                controlUnit.node = null
                cur.save(controlUnit)
                return cu.toDTO()
            }
            else ->{
                if (cu.node!!.id  == c.nodeId)
                    return cur.save(controlUnit).toDTO()
                else {
                    val newNode = nr.findById(c.nodeId!!).get()
                    controlUnit.node = newNode
                    val newC = cur.save(controlUnit)
                    newNode.controlUnits.add(newC)
                    nr.save(newNode)
                    return newC.toDTO()
                }

            }
        }
/*
        if( (c.nodeId == null && cu.node == null) || (c.nodeId != null && cu.node!= null && c.nodeId == cu.node!!.id) ){

            cu.apply {
                name = c.name
                remainingBattery = c.remainingBattery
                rssi = c.rssi
            }
            cur.save(cu)
            return cu.toDTO()

        }

        val newNode = nr.findById(c.nodeId).get()
        val controlUnit = cu.apply {
            name = c.name
            remainingBattery = c.remainingBattery
            rssi = c.rssi
            node = newNode

        }
        cu.node.controlUnits.remove(cu)
        nr.save(cu.node)
        val newC = cur.save(controlUnit)
        newNode.controlUnits.add(newC)
        nr.save(newNode)
        return newC.toDTO()

 */

    }

    override fun delete(id: Long) {
       cur.deleteById(id)
    }
}