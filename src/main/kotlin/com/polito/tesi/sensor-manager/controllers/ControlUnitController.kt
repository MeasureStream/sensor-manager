package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
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

    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/","")
    fun deleteCU(@Valid @RequestBody cu :ControlUnitDTO){
        return cs.delete(cu.id)
    }
}