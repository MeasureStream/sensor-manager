package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class MeasurementUnitServiceImpl(private val mur:MeasurementUnitRepository,private val nr: NodeRepository) : MeasurementUnitService {
    override fun get(id: Long): MeasurementUnitDTO? {
        //return mur.findByIdOrNull(id)?.toDTO()
        val userId = getCurrentUserId()
        return mur.findByIdAndNode_OwnerId(id,userId)?.toDTO() ?: throw EntityNotFoundException("MeasurementUnit $id not found")
    }

    override fun getAll(networkId: Long?, controlUnitNId: Long?, controlUnitName: String?): List<MeasurementUnitDTO> {
        val userId = getCurrentUserId()
        networkId?.let {
            //return mur.findAllByNetworkId(it).map { e->e.toDTO()
            return mur.findAllByNetworkIdAndNode_OwnerId(it,userId).map { e->e.toDTO() }
            }
        return mur.findAllByNode_OwnerId(userId).map { it.toDTO() }
        //return mur.findAll().map { it.toDTO() }
    }

    @Transactional
    override fun getByNodeId(nodeId:Long): List<MeasurementUnitDTO>{
        val n = nr.findById(nodeId).get()
        if(n.ownerId != getCurrentUserId()) throw OperationNotAllowed("You can't get a MeasurementUnit owned by someone else")
        return n.measurementUnits.toList().map { it.toDTO() }
    }

    override fun getAllPage(
        page: Pageable,
        networkId: Long?,
        controlUnitNId: Long?,
        controlUnitName: String?
    ): Page<MeasurementUnitDTO> {
        networkId?.let {
            //return mur.findAllByNetworkId(it,page).map { e->e.toDTO() }
            return mur.findAllByNetworkIdAndNode_OwnerId(it,getCurrentUserId(),page).map { e->e.toDTO() }
        }

        //return mur.findAll(page).map { it.toDTO() }
        return mur.findAllByNode_OwnerId(getCurrentUserId(),page).map { it.toDTO() }
    }
    @Transactional
    override fun create(m: MeasurementUnitDTO): MeasurementUnitDTO {
        val userId = getCurrentUserId()

        if( mur.findByNetworkId(m.networkId) != null ) throw EntityExistsException("MU ${m.id} already present")
        if( m.nodeId != null && nr.findById(m.nodeId).isEmpty ) throw EntityNotFoundException("Node ${m.nodeId} not exists ")

        val n = if (m.nodeId != null )  nr.findById(m.nodeId).get() else null
        if(n != null && n.ownerId != userId) throw OperationNotAllowed("You can't create a MeasurementUnit owned by someone else")

        val measurementUnit = MeasurementUnit().apply {
            type = m.type
            measuresUnit = m.measuresUnit
            networkId = m.networkId
            idDcc = m.idDcc ?: 0
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
        val userId = getCurrentUserId()

        //if( id != m.id ) throw Exception("can't update")
        val mu = mur.findById(id).get()

        if(mu.networkId != m.networkId){
            throw  EntityNotFoundException()// questa exception è da sostituire quando il network_id non può essere mai cambiato oppure fare il controllo di non prendere il network id dall'esterno ma io non lo cambierei mai
        }
        val measurementUnit = mu.apply {
            type = m.type
            measuresUnit = m.measuresUnit
            idDcc = m.idDcc ?: 0
        }

        when {
            mu.node == null && m.nodeId == null -> {
                mur.save(measurementUnit)
                return mu.toDTO()
            }
            mu.node == null && m.nodeId != null -> {
                val newNode = nr.findById(m.nodeId).get()
                if(newNode.ownerId != userId) throw OperationNotAllowed("You can't update a MeasurementUnit owned by someone else")
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
                    if(newNode.ownerId != userId) throw OperationNotAllowed("You can't update a MeasurementUnit owned by someone else")
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
        val mu = mur.findById(id).get()
        if(mu.node?.ownerId != getCurrentUserId()) throw OperationNotAllowed("You can't delete a MeasurementUnit owned by someone else")
        mur.deleteById(id)
    }

    fun getCurrentUserId(): String {
        val auth = SecurityContextHolder.getContext().authentication
        val jwt = auth.principal as Jwt
        return jwt.subject  // oppure jwt.getClaim<String>("preferred_username")
    }

}