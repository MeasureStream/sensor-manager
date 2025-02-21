package com.polito.tesi.measuremanager.services
/*
import com.polito.tesi.measuremanager.dtos.MeasureDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.entities.Measures
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasureRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant
@Service
class MeasureServiceImpl(private val mr: MeasureRepository,private val cr: ControlUnitRepository, private val mur: MeasurementUnitRepository ):MeasureService {
    override fun get(id: Long): MeasureDTO? {
        return mr.findByIdOrNull(id)?.toDTO()
    }

    override fun getAllMeasures(
        measurementUnitNId: Long?,
        controlUnitNid: Long?,
        controlUnitName: String?
    ): List<MeasureDTO> {
        measurementUnitNId?.let {  }
        controlUnitNid?.let {  }
        controlUnitName?.let {  }
        return mr.findAll().map { it.toDTO() }
    }

    override fun getAllMeasuresPage(
        page: Pageable,
        measurementUnitNId: Long?,
        controlUnitNid: Long?,
        controlUnitName: String?
    ): Page<MeasureDTO> {
        measurementUnitNId?.let {  }
        controlUnitNid?.let {  }
        controlUnitName?.let {  }
        return mr.findAll(page).map { it.toDTO() }
    }

    override fun create(m: MeasureDTO): MeasureDTO {
        val c = cr.findByNetworkId(m.controlUnitNId)
        val mu = mur.findByNetworkId(m.measurementUnitNId)
        if( c == null || mu == null)
            throw Error("CU_NetworkID ${m.controlUnitNId} or MU_NetworkID ${m.measurementUnitNId} not present")
        val measure = Measures().apply{
            _value = m.value
            measureUnit = m.measureUnit
            time = m.time
            measurementUnit = mu
            controlUnit = c

        }
        mu.measures.add(measure)
        c.measures.add(measure)

        mr.save(measure)
        cr.save(c)
        mur.save(mu)

        return measure.toDTO()

    }

    override fun update(measureId: Long, m: MeasureDTO): MeasureDTO {
        TODO("Not yet implemented")
    }

    override fun delete(measureId: Long) {
        TODO("Not yet implemented")
    }
}
*/