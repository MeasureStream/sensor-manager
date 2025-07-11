package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.services.MeasurementUnitService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/API/measurementunits")
class MeasurementUnitController(
    private val mus: MeasurementUnitService,
) {

    @GetMapping("/","")
    fun get( @RequestParam networkId: Long?, @RequestParam controlUnitName: String?, @RequestParam controlUnitNId : Long?  ): List<MeasurementUnitDTO> {
        return mus.getAll(networkId = networkId, controlUnitNId = controlUnitNId, controlUnitName = controlUnitName)
    }
    @GetMapping("/nodeid/")
    fun getByNodeId(@RequestParam(required = true) nodeId: Long ):List<MeasurementUnitDTO>{
        return mus.getByNodeId(nodeId)
    }
    @GetMapping("/available","/available/")
    fun getAvailable(  ): List<MeasurementUnitDTO> {
        return mus.getAvailable()
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/","")
    fun createMU(@Valid @RequestBody mu:MeasurementUnitDTO):MeasurementUnitDTO{
        return mus.create(mu)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PutMapping("/","")
    fun updateMU(@Valid @RequestBody mu: MeasurementUnitDTO): MeasurementUnitDTO {
        return mus.update(mu.id,mu)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/","")
    fun deleteMU( @RequestBody mu: MeasurementUnitDTO) {
        mus.delete(mu.id)
    }

    @GetMapping("/firstavailable", "/firstavailable/")
    fun getFirstAvailableNId():Long{
        return mus.getFirstAvailableNId()
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/admin-create","/admin-create/")
    fun createMUforUser(@Valid @RequestBody mu:MeasurementUnitDTO, @RequestParam(required = true) userId:String):MeasurementUnitDTO{
        //return mus.create(mu)
        return mus.createforUser(mu, userId)
    }
}