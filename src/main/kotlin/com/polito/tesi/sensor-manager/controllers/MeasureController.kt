package com.polito.tesi.measuremanager.controllers

/*
import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasureDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.services.ControlUnitService
import com.polito.tesi.measuremanager.services.MeasureService
import com.polito.tesi.measuremanager.services.MeasureServiceImpl
import com.polito.tesi.measuremanager.services.MeasurementUnitService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Validated
@RestController
@RequestMapping("/API/measures")
class MeasureController (
    private val ms: MeasureService,

) {
    @GetMapping("/","")
    fun lifeCheck():String {
        return "alive"
    }
    @GetMapping("/all","/all/")
    fun getAll(@RequestParam measurementUnitNId : Long?,@RequestParam controlUnitNId: Long?, @RequestParam controlUnitName:String? ):List<MeasureDTO>{
        return ms.getAllMeasures(measurementUnitNId,controlUnitNId,controlUnitName)
    }
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/","")
    fun create(@Valid @RequestBody m : MeasureDTO):MeasureDTO{
        return ms.create(m)
    }



    @PutMapping("/","")
    fun update(@Valid @RequestBody m : MeasureDTO):MeasureDTO{
        return ms.update(m.id, m)
    }




}

 */