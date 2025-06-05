package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.toCuCreateDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Node
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.kafka.KafkaCuProducer
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
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
import kotlin.jvm.optionals.getOrNull
@Service
class ControlUnitServiceImpl(private val cur: ControlUnitRepository, private val nr: NodeRepository, private val ur: UserRepository , private val kcu:KafkaCuProducer):ControlUnitService {
    override fun getAllControlUnits(networkId: Long?, name: String?): List<ControlUnitDTO> {

        if(isAdmin()){
            networkId?.let {
                return cur.findAllByNetworkId(it).map { e-> e.toDTO() }

            }
            name?.let {

                return cur.findAllByName(it).map { e-> e.toDTO() }
            }
            return cur.findAll().map { it.toDTO() }

        }

        val userId = getCurrentUserId()

        networkId?.let {

            return cur.findAllByNetworkIdAndUser_UserId(it, userId).map { e-> e.toDTO() }
        }
        name?.let {
            return cur.findAllByNameAndUser_UserId(it, userId).map { e-> e.toDTO() }

        }
        return cur.findAllByUser_UserId(userId).map { it.toDTO() }

    }

    override fun getControlUnit(id: Long): ControlUnitDTO? {
        if(isAdmin())
            return cur.findByIdOrNull(id)?.toDTO()
        val userId = getCurrentUserId()
        return cur.findByIdAndUser_UserId(id,userId)?.toDTO() ?: throw EntityNotFoundException("ControlUnit $id not found")

    }



    override fun getAllControlUnitsPage(page: Pageable, networkId: Long?, name: String?): Page<ControlUnitDTO> {
        if(isAdmin()){
            networkId?.let {
                return cur.findAllByNetworkId(it,page).map { e-> e.toDTO() }

            }
            name?.let {
                return cur.findAllByName(it,page).map { e-> e.toDTO() }

            }
            return cur.findAll(page).map { it.toDTO() }

        }
        val userId = getCurrentUserId()
        networkId?.let {
            //return cur.findAllByNetworkId(it,page).map { e-> e.toDTO() }
            return cur.findAllByNetworkIdAndUser_UserId(it,userId,page).map { e-> e.toDTO() }
        }
        name?.let {
            //return cur.findAllByName(it,page).map { e-> e.toDTO() }
            return cur.findAllByNameAndUser_UserId(it,userId,page).map { e-> e.toDTO() }
        }
        //return cur.findAll(page).map { it.toDTO() }
        return cur.findAllByUser_UserId(userId,page).map { it.toDTO() }
    }

    override fun getByNodeId(nodeId: Long): List<ControlUnitDTO> {
        val userId = getCurrentUserId()
        val n = nr.findById(nodeId).get()
        if(n.user.userId != userId && !isAdmin() ) throw OperationNotAllowed("You can't get a ControlUnit owned by someone else")
        return n.controlUnits.toList().map { it.toDTO() }
    }

    @Transactional
    override fun create(c: ControlUnitDTO): ControlUnitDTO {
        // da riscrivere una funzione ad hoc per admin
        val user = getOrCreateCurrentUserId()

        if(cur.findByNetworkId(c.networkId) != null ) throw EntityExistsException()//Exception("CU_NetworkID : ${c.networkId} already present")
        if( c.remainingBattery > 100.0 || c.remainingBattery<0.0 ) throw OperationNotAllowed("remain battery out of range c.remainingBattery: ${c.remainingBattery}")
        if( c.nodeId != null && nr.findById(c.nodeId).isEmpty ) throw EntityNotFoundException("Node ${c.nodeId} not exists ")

        val n = if (c.nodeId != null ) nr.findById(c.nodeId).get() else null
        if(n != null && n.user.userId != user.userId) throw OperationNotAllowed("You can't create a ControlUnit owned by someone else")
        val cu = ControlUnit().apply {
            networkId = c.networkId
            name = c.name
            remainingBattery = c.remainingBattery
            rssi = c.rssi
            node = n
            this.user = user
        }
        user.cus.add(cu)
        ur.save(user)

        val savedC = cur.save(cu)
        if (n != null) {
            n.controlUnits.add(savedC)
            nr.save(n)
        }

        kcu.sendCuCreate(savedC.toCuCreateDTO())
        return savedC.toDTO()
    }
    @Transactional
    override fun update(id: Long, c: ControlUnitDTO): ControlUnitDTO {
        // da riscrivere una funzione ad hoc per admin

        if( id != c.id ) throw Exception("can't update")
        val cu = cur.findById(id).get()
        val userId = if(isAdmin()) cu.user.userId else getCurrentUserId()

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
                if(newNode.user.userId != userId) throw OperationNotAllowed("You can't update a ControlUnit owned by someone else")
                controlUnit.node = newNode
                val newC = cur.save(controlUnit)
                newNode.controlUnits.add(newC)
                nr.save(newNode)
                return newC.toDTO()
            }
            cu.node != null  && c.nodeId == null -> {
                if(cu.node!!.user.userId != userId) throw OperationNotAllowed("You can't update a ControlUnit owned by someone else")
                controlUnit.node = null
                cur.save(controlUnit)
                return cu.toDTO()
            }
            else ->{
                if(cu.node!!.user.userId != userId) throw OperationNotAllowed("You can't update a ControlUnit owned by someone else")
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
        val userId = getCurrentUserId()
        val cu = cur.findById(id).get()
        if( cu.user.userId != userId && !isAdmin() ) throw OperationNotAllowed("You can't delete a ControlUnit owned by someone else")
        cur.deleteById(id)
    }

    override fun getAvailable(): List<ControlUnitDTO> {
        if(isAdmin())
            return cur.findAllByNodeIsNull().map { it.toDTO() }
        val userId = getCurrentUserId()
        return cur.findAllByNodeIsNullAndUser_UserId(userId).map { it.toDTO() }
    }
    override fun getFirstAvailableNId():Long{
        return  (cur.findMaxNetworkId() ?: 0L) + 1

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

    fun isAdmin() : Boolean{
        val auth = SecurityContextHolder.getContext().authentication
        val isAdmin = auth.authorities.any { it.authority == "ROLE_app-admin" }
        return isAdmin
    }

}