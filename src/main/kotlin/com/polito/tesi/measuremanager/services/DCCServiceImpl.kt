package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.DCCDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.DCC
import com.polito.tesi.measuremanager.repositories.DCCRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DCCServiceImpl(private val dccr: DCCRepository) : DCCService {
    override fun getDcc(id: Long): DCCDTO? {
        TODO("Not yet implemented")
    }

    override fun getAllDccs(): List<DCCDTO> {
        return dccr.findAll().map { it.toDTO() }
    }

    override fun getAllDccsPage(page: Pageable): Page<DCCDTO> {
        TODO("Not yet implemented")
    }

    override fun getByNodeId(nodeId: Long): DCCDTO? {
        TODO("Not yet implemented")
    }

    override fun create(dcc: DCCDTO, pdf: MultipartFile): DCCDTO {
        val dccEntity = DCC().apply {
            scadenza = dcc.scadenza
            this.pdf = pdf.bytes
        }
        dccr.save(dccEntity)
        return dccEntity.toDTO()
    }

    override fun update(dcc: DCCDTO, pdf: MultipartFile): DCCDTO {
        TODO("Not yet implemented")
    }


    override fun delete(id: Long) {
        TODO("Not yet implemented")
    }
}