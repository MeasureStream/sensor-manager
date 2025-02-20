package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.NodeDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.Node
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
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

@Service
class NodeServiceImpl ( private val nr: NodeRepository, private val cur:ControlUnitRepository , private val mur: MeasurementUnitRepository):NodeService {
    override fun getNode(id: Long): NodeDTO {
        return nr.findById(id).get().toDTO()
    }

    override fun getAllNodes( name: String?): List<NodeDTO> {
        if ( !name.isNullOrBlank() ) {
           return nr.findAllByName( name ).map { it.toDTO() }
        }
        return nr.findAll().map { it.toDTO() }
    }

    override fun getAllNodesPage(page: Pageable, name: String?): Page<NodeDTO> {
        if ( !name.isNullOrBlank() ) {
            return nr.findAllByName( name , page).map { it.toDTO() }
        }
        return nr.findAll(page).map { it.toDTO() }
    }

    @Transactional
    override fun create(n: NodeDTO): NodeDTO {
        //if( nr.findById(n.id).isPresent ) throw EntityExistsException("Node ${n.id} already present")
        val node = Node().apply{
            name = n.name
            standard = n.standard
            controlUnits = mutableSetOf()
            measurementUnits = mutableSetOf()
            location = n.location
        }

        val savedNode = nr.save(node)

        if(n.controlUnitsId.isNotEmpty()){
            val controlUnits = n.controlUnitsId.map {  cur.findById(it).getOrElse { throw  NoSuchElementException() }}.toMutableSet()
            if(controlUnits.any { it.node != null && it.node!!.id != n.id  })
                throw OperationNotAllowed("A ControlUnit is already assigned ${controlUnits.filter { it.node != null && it.node!!.id != n.id  }}")
            controlUnits.forEach { it.node = savedNode }
            cur.saveAll(controlUnits)
            node.controlUnits = controlUnits
        }
        if(n.measurementUnitsId.isNotEmpty()){
            val measurementUnits = n.measurementUnitsId.map {  mur.findById(it).getOrElse { throw  NoSuchElementException() }}.toMutableSet()
            if(measurementUnits.any { it.node != null && it.node!!.id != n.id  })
                throw OperationNotAllowed("A MeasurementUnits is already assigned ${measurementUnits.filter { it.node != null && it.node!!.id != n.id  }}")
            measurementUnits.forEach { it.node = savedNode }
            mur.saveAll(measurementUnits)
            node.measurementUnits = measurementUnits
        }


        return nr.save(node).toDTO()


    }

    @Transactional
    override fun update(id: Long, n: NodeDTO): NodeDTO {
        val n_old = nr.findById(id).get()
        val node = n_old.apply{
            name = n.name
            standard = n.standard
            location = n.location

        }
        //println(node.toDTO())
        //val controlUnitIdToAdd = n.controlUnitsId.filter {c -> ! n_old.controlUnits.map { it.id }.contains(c) }
        //val controlUnitIdToRemove =  n_old.controlUnits.map { it.id }.filter { ! n.controlUnitsId.contains(it) }

        val savedNode = nr.save(node)
        if(n.controlUnitsId.isNotEmpty()){
            val controlUnits = n.controlUnitsId.map {  cur.findById(it).getOrElse { throw  NoSuchElementException() }}.toMutableSet()
            if(controlUnits.filter { it.node != null }.any { c -> !n_old.controlUnits.map { it.id }.contains(c.id) })
                throw OperationNotAllowed("A ControlUnit is already assigned to another Node ${controlUnits.filter{ (it.node?.id ?: n_old.id) != n_old.id  }.map { it.id } } ")
            println( controlUnits.filter { it.node == null }.map { it.id })
            controlUnits.filter { it.node == null }.forEach { it.node = savedNode }
            cur.saveAll(controlUnits)
            savedNode.controlUnits.addAll(controlUnits)
        }

        if(n.measurementUnitsId.isNotEmpty()){
            val measurementUnits = n.measurementUnitsId.map {  mur.findById(it).getOrElse { throw  NoSuchElementException() }}.toMutableSet()
            if(measurementUnits.filter { it.node != null }.any {  m -> !n_old.measurementUnits.map { it.id }.contains(m.id) } )
                throw OperationNotAllowed("A MeasurementUnits is already assigned")
            measurementUnits.filter { it.node == null }.forEach { it.node = savedNode }
            mur.saveAll(measurementUnits)
            savedNode.measurementUnits.addAll( measurementUnits)
        }

        return nr.save(savedNode).toDTO()

    }

    override fun delete(id: Long) {
        nr.deleteById(id)
    }
}