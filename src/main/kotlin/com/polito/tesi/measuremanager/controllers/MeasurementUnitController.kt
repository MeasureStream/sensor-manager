package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.services.MeasurementUnitService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

@RestController
@RequestMapping("/API/measurementunits")
class MeasurementUnitController(
    private val mus: MeasurementUnitService,
) {
    private val logger = LoggerFactory.getLogger(MeasurementUnitController::class.java)

    @GetMapping("/", "")
    fun get(
        @RequestParam extendedId: Long?,
        @RequestParam controlUnitName: String?,
        @RequestParam controlUnitDevEUI: Long?,
    ): List<MeasurementUnitDTO> {
        // --- DEBUG START ---
        val auth = SecurityContextHolder.getContext().authentication
        logger.info("--- NUOVA RICHIESTA GET /API/measurementunits ---")
        logger.info("Parametri: extendedId=$extendedId, controlUnitName=$controlUnitName, devEUI=$controlUnitDevEUI")

        if (auth == null) {
            logger.error("ERRORE: Authentication è NULL! La richiesta non è passata dai filtri di sicurezza.")
        } else {
            logger.info("Principal: ${auth.name}")
            logger.info("Authorities: ${auth.authorities}")
            logger.info("Is Authenticated: ${auth.isAuthenticated}")
        }
        // --- DEBUG END ---

        return mus.getAll(extendedId = extendedId, controlUnitDevEUI = controlUnitDevEUI, controlUnitName = controlUnitName)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PutMapping("/", "")
    fun updateMU(
        @Valid @RequestBody mu: MeasurementUnitDTO,
    ): MeasurementUnitDTO {
        return mus.update(mu.id, mu)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/", "")
    fun deleteMU(
        @RequestBody mu: MeasurementUnitDTO,
    ) {
        mus.delete(mu.id)
    }
}
