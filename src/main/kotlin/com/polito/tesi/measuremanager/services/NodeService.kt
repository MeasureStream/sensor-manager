package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.NodeDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface NodeService {
    fun getNode(id:Long): NodeDTO
    fun getAllNodes(  name: String? ):List<NodeDTO>
    fun getAllNodesPage(page: Pageable, name: String? ): Page<NodeDTO>

    fun create(c: NodeDTO): NodeDTO
    fun update(id: Long, n: NodeDTO): NodeDTO
    fun delete(id: Long)
}