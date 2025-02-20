package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.MeasureDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface MeasureService {
    fun get(id:Long):MeasureDTO?
    fun getAllMeasures(measurementUnitNId: Long? , controlUnitNid: Long? , controlUnitName: String? ) : List<MeasureDTO>
    fun getAllMeasuresPage(page:Pageable, measurementUnitNId: Long? , controlUnitNid: Long? , controlUnitName: String?): Page<MeasureDTO>

    fun create(m: MeasureDTO ):MeasureDTO
    fun update(measureId: Long, m : MeasureDTO ) : MeasureDTO
    fun delete(measureId: Long)

}