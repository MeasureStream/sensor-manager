package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.services.ControlUnitService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RestController
@RequestMapping("/API/controlunits")
class ControlUnitController(
    private val cs: ControlUnitService,
) {
    @GetMapping("/", "")
    fun get(
        @RequestParam name: String?,
    ): List<ControlUnitDTO> {
        return cs.getAllControlUnits( name = name)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PutMapping("/", "")
    fun updateCU(
        @Valid @RequestBody cu: ControlUnitDTO,
    ): ControlUnitDTO  {
        return cs.update(cu.id, cu)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/", "")
    fun deleteCU(
        @RequestParam id: Long,
    )  {
        return cs.delete(id)
    }
}
