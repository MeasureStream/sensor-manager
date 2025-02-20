package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.Node
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository

@Repository
interface NodeRepository: JpaRepository<Node, Long>, PagingAndSortingRepository<Node, Long> {
    fun findAllByName(name:String) : List<Node>
    fun findAllByName(name:String, pageable: Pageable) : Page<Node>
}