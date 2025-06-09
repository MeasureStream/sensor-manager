package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.services.ControlUnitService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/API/controlunits")
class ControlUnitController(
    private val cs: ControlUnitService
) {
    @GetMapping("/","")
    fun get( @RequestParam networkId: Long?, @RequestParam name: String? ): List<ControlUnitDTO> {
        return cs.getAllControlUnits(networkId = networkId, name = name)
    }

    @GetMapping("/nodeid/")
    fun getByNodeId(@RequestParam(required = true) nodeId: Long ):List<ControlUnitDTO>{
        return cs.getByNodeId(nodeId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/","")
    fun createCU(@Valid @RequestBody cu : ControlUnitDTO): ControlUnitDTO {
        return cs.create(cu)
    }
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PutMapping("/","")
    fun updateCU(@Valid @RequestBody cu :ControlUnitDTO): ControlUnitDTO{
        return cs.update(cu.id,cu)
    }

    @GetMapping("/available","/available/")
    fun getAvailable(  ): List<ControlUnitDTO> {
        return cs.getAvailable()
    }


    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/","")
    fun deleteCU(@Valid @RequestBody cu :ControlUnitDTO){
        return cs.delete(cu.id)
    }
    @GetMapping("/firstavailable", "/firstavailable/")
    fun getFirstAvailableNId():Long{
        return cs.getFirstAvailableNId()
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/admin-create","/admin-create/")
    fun createCUforUser(@Valid @RequestBody cu : ControlUnitDTO, @RequestParam(required = true) userId:String): ControlUnitDTO {
        return cs.createforUser(cu,userId)
    }
}