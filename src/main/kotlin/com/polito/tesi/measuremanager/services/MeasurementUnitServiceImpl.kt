package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class MeasurementUnitServiceImpl(private val mur:MeasurementUnitRepository,private val nr: NodeRepository) : MeasurementUnitService {
    override fun get(id: Long): MeasurementUnitDTO? {
        return mur.findByIdOrNull(id)?.toDTO()
    }

    override fun getAll(networkId: Long?, controlUnitNId: Long?, controlUnitName: String?): List<MeasurementUnitDTO> {
        networkId?.let { return mur.findAllByNetworkId(it).map { e->e.toDTO() } }

        return mur.findAll().map { it.toDTO() }
    }

    override fun getByNodeId(nodeId:Long): List<MeasurementUnitDTO>{
        val n = nr.findById(nodeId).get()
        return n.measurementUnits.toList().map { it.toDTO() }
    }

    override fun getAllPage(
        page: Pageable,
        networkId: Long?,
        controlUnitNId: Long?,
        controlUnitName: String?
    ): Page<MeasurementUnitDTO> {
        networkId?.let {return mur.findAllByNetworkId(it,page).map { e->e.toDTO() }  }

        return mur.findAll(page).map { it.toDTO() }
    }
    @Transactional
    override fun create(m: MeasurementUnitDTO): MeasurementUnitDTO {

        if( mur.findByNetworkId(m.networkId) != null ) throw EntityExistsException("MU ${m.id} already present")
        if( m.nodeId != null && nr.findById(m.nodeId).isEmpty ) throw EntityNotFoundException("Node ${m.nodeId} not exists ")

        val n = if (m.nodeId != null )  nr.findById(m.nodeId).get() else null

        val measurementUnit = MeasurementUnit().apply {
            type = m.type
            measuresUnit = m.measuresUnit
            networkId = m.networkId
            idDcc = m.idDcc
            node = n

        }
        val savedM = mur.save(measurementUnit)

        if (n != null) {
            n.measurementUnits.add(savedM)
            nr.save(n)
        }


        return savedM.toDTO()

    }
    @Transactional
    override fun update(id: Long, m: MeasurementUnitDTO): MeasurementUnitDTO {


        //if( id != m.id ) throw Exception("can't update")
        val mu = mur.findById(id).get()

        if(mu.networkId != m.networkId){
            throw  EntityNotFoundException()// questa exception è da sostituire quando il network_id non può essere mai cambiato oppure fare il controllo di non prendere il network id dall'esterno ma io non lo cambierei mai
        }
        val measurementUnit = mu.apply {
            type = m.type
            measuresUnit = m.measuresUnit
            idDcc = m.idDcc
        }

        when {
            mu.node == null && m.nodeId == null -> {
                mur.save(measurementUnit)
                return mu.toDTO()
            }
            mu.node == null && m.nodeId != null -> {
                val newNode = nr.findById(m.nodeId).get()
                measurementUnit.node = newNode
                val newC = mur.save(measurementUnit)
                newNode.measurementUnits.add(newC)
                nr.save(newNode)
                return newC.toDTO()
            }
            mu.node != null  && m.nodeId == null -> {
                measurementUnit.node = null
                mur.save(measurementUnit)
                return mu.toDTO()
            }
            else ->{
                if (mu.node!!.id  == m.nodeId)
                    return mur.save(measurementUnit).toDTO()
                else {
                    val newNode = nr.findById(m.nodeId!!).get()
                    measurementUnit.node = newNode
                    val newC = mur.save(measurementUnit)
                    newNode.measurementUnits.add(newC)
                    nr.save(newNode)
                    return newC.toDTO()
                }

            }
        }

        /*
        if(m.nodeId == mu.node.id){
            val measurementUnit = mu.apply {
                type = m.type
                measuresUnit = m.measuresUnit
                idDcc = m.idDcc
            }
            mur.save(measurementUnit)
            return measurementUnit.toDTO()
        }

        val newNode = nr.findById(m.nodeId).get()
        val measurementUnit = mu.apply {
            type = m.type
            measuresUnit = m.measuresUnit
            networkId = m.networkId
            idDcc = m.idDcc
            node = newNode

        }
        mu.node.measurementUnits.remove(mu)
        nr.save(mu.node)
        val newM = mur.save(measurementUnit)
        newNode.measurementUnits.add(newM)
        nr.save(newNode)
        return newM.toDTO()

         */
    }

    override fun delete(id: Long) {
        mur.deleteById(id)
    }


}