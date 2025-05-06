package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.DCCDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.DCC
import com.polito.tesi.measuremanager.repositories.DCCRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DCCServiceImpl(private val dccr: DCCRepository, private val mur: MeasurementUnitRepository) : DCCService {
    override fun getDcc(id: Long): DCCDTO? {
        TODO("Not yet implemented")
    }

    override fun getpdf(id: Long): ByteArray? {
        return dccr.findById(id).orElse(null)?.pdf
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
            expiration = dcc.expiration
            this.pdf = pdf.bytes
            filename = dcc.filename

        }
        if(dcc.muId != null && mur.findById(dcc.muId).isPresent) {
            val mu = mur.findById(dcc.muId).get()
            dccEntity.mu = mu
            mu.dcc = dccEntity
            dccr.save(dccEntity)
            mur.save(mu)
        } else{
            dccr.save(dccEntity)
        }

        return dccEntity.toDTO()
    }

    override fun update(dcc: DCCDTO, pdf: MultipartFile): DCCDTO {
        TODO("Not yet implemented")
    }


    override fun delete(id: Long) {
        val dcc = dccr.findById(id).orElse(null)
        if(dcc != null) {
            val mu = mur.findById(dcc.mu!!.id).orElse(null)
            if (mu != null) {
                mu.dcc = null
                dcc.mu = null
                mur.save(mu)
                dccr.save(dcc)
            }
            dccr.delete(dcc)
        }

    }
}