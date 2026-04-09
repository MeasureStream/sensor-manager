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
        // Questo lo vedrai nei log non appena la richiesta "tocca" il controller
        println(">>> DEBUG SENSOR-MANAGER: Chiamata ricevuta su /polling!")
        println(">>> DATA: $command")

        try {
            cs.sendPollingUpdate(command)
            println(">>> DEBUG SENSOR-MANAGER: Service eseguito correttamente")
        } catch (e: Exception) {
            println(">>> DEBUG ERROR: Qualcosa è fallito nel service!")
            e.printStackTrace() // Questo stampa tutto l'errore nei log di Docker
            throw e
        }
    }
}
