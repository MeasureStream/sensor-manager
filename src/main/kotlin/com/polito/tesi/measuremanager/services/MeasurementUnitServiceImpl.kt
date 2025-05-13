package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Node
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import com.polito.tesi.measuremanager.repositories.UserRepository
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrElse

@Service
class MeasurementUnitServiceImpl(private val mur:MeasurementUnitRepository,private val nr: NodeRepository, private val ur: UserRepository) : MeasurementUnitService {
    override fun get(id: Long): MeasurementUnitDTO? {
        //return mur.findByIdOrNull(id)?.toDTO()
        val userId = getCurrentUserId()
        return mur.findByIdAndUser_UserId(id,userId)?.toDTO() ?: throw EntityNotFoundException("MeasurementUnit $id not found")
    }

    override fun getAll(networkId: Long?, controlUnitNId: Long?, controlUnitName: String?): List<MeasurementUnitDTO> {
        val userId = getCurrentUserId()
        networkId?.let {
            //return mur.findAllByNetworkId(it).map { e->e.toDTO()
            return mur.findAllByNetworkIdAndUser_UserId(it,userId).map { e->e.toDTO() }
            }
        return mur.findAllByUser_UserId(userId).map { it.toDTO() }
        //return mur.findAll().map { it.toDTO() }
    }

    @Transactional
    override fun getByNodeId(nodeId:Long): List<MeasurementUnitDTO>{
        val n = nr.findById(nodeId).get()
        if(n.user.userId != getCurrentUserId()) throw OperationNotAllowed("You can't get a MeasurementUnit owned by someone else")
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
            return mur.findAllByNetworkIdAndUser_UserId(it,getCurrentUserId(),page).map { e->e.toDTO() }
        }

        //return mur.findAll(page).map { it.toDTO() }
        return mur.findAllByUser_UserId(getCurrentUserId(),page).map { it.toDTO() }
    }
    @Transactional
    override fun create(m: MeasurementUnitDTO): MeasurementUnitDTO {
        val user = getOrCreateCurrentUserId()

        if( mur.findByNetworkId(m.networkId) != null ) throw EntityExistsException("MU NetworkId ${m.networkId} already present")
        if( m.nodeId != null && nr.findById(m.nodeId).isEmpty ) throw EntityNotFoundException("Node ${m.nodeId} not exists ")

        val n = if (m.nodeId != null )  nr.findById(m.nodeId).get() else null
        if(n != null && n.user.userId != user.userId) throw OperationNotAllowed("You can't create a MeasurementUnit owned by someone else")

        val measurementUnit = MeasurementUnit().apply {
            type = m.type
            measuresUnit = m.measuresUnit
            networkId = m.networkId
            //idDcc = m.idDcc ?: 0
            node = n
            this.user = user

        }
        user.mus.add(measurementUnit)
        ur.save(user)
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
            //idDcc = m.idDcc ?: 0
        }

        when {
            mu.node == null && m.nodeId == null -> {
                mur.save(measurementUnit)
                return mu.toDTO()
            }
            mu.node == null && m.nodeId != null -> {
                val newNode = nr.findById(m.nodeId).get()
                if(newNode.user.userId != userId) throw OperationNotAllowed("You can't update a MeasurementUnit owned by someone else")
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
                    if(newNode.user.userId != userId) throw OperationNotAllowed("You can't update a MeasurementUnit owned by someone else")
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
        if( mu.user.userId != getCurrentUserId()) throw OperationNotAllowed("You can't delete a MeasurementUnit owned by someone else")
        mur.deleteById(id)
    }

    override fun getAvailable(): List<MeasurementUnitDTO> {
        val userId = getCurrentUserId()
        return mur.findByNodeIsNullAndUser_UserId(userId).map { it.toDTO() }
    }

    fun getCurrentUserId(): String {
        val auth = SecurityContextHolder.getContext().authentication
        val jwt = auth.principal as Jwt
        return jwt.subject  // oppure jwt.getClaim<String>("preferred_username")
    }


    fun getCurrentUserInfo(): Map<String, String?> {
        val auth = SecurityContextHolder.getContext().authentication
        val jwt = auth.principal as Jwt
        return mapOf(
            "userId" to jwt.subject,
            "email" to jwt.getClaim<String>("email"),
            "givenName" to jwt.getClaim<String>("given_name"),
            "familyName" to jwt.getClaim<String>("family_name"),
            "preferredUsername" to jwt.getClaim<String>("preferred_username")
        )
    }

    fun getOrCreateCurrentUserId(): User {
        val userId = getCurrentUserId()
        val user = ur.findById(userId).getOrElse { null }
        if( user != null)
            return user
        val info = getCurrentUserInfo()
        val newUser = User().apply {
            this.userId = userId
            name = info["givenName"] ?: ""
            surname = info["familyName"] ?: ""
            email = info["email"] ?: ""
            nodes = mutableSetOf<Node>()
            mus = mutableSetOf()
            cus = mutableSetOf()
        }

        return ur.save(newUser)
    }

}