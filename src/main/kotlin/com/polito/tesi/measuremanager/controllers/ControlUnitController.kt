package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.CUConfigCommandDTO
import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.services.ControlUnitService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

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

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/polling", "/polling/")
    fun configureCU(
        @RequestBody command: CUConfigCommandDTO,
    ) {
        // Chiamiamo un metodo specifico del service
        cs.sendPollingUpdate(command)
    }
}
