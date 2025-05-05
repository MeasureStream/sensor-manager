package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.DCCDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.multipart.MultipartFile


interface DCCService {
    fun getDcc(id: Long): DCCDTO?
    fun getAllDccs(): List<DCCDTO>
    fun getAllDccsPage(page: Pageable): Page<DCCDTO>
    fun getByNodeId(nodeId: Long): DCCDTO?
    fun create(dcc : DCCDTO, pdf: MultipartFile): DCCDTO
    fun update(dcc : DCCDTO, pdf: MultipartFile): DCCDTO
    fun delete(id: Long)
    fun getpdf(id: Long): ByteArray?
}