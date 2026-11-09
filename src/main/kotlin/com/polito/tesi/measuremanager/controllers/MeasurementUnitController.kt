package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.DeviceEventDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitSummaryDTO
import com.polito.tesi.measuremanager.services.MeasurementUnitService
import jakarta.persistence.EntityNotFoundException
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

    /**
     * L'elenco della pagina «Mie MU»: tutte le MU che ho usato nel tempo, montate adesso o no.
     *
     * Sta su un percorso suo e non su `GET /` perche' porta altro: lo stato, l'ultima CU, i
     * conteggi degli avvisi. Il DTO completo trascinerebbe i sensori di ognuna con i loro
     * riferimenti di template, cioe' molti piu' dati di quelli che la tabella mostra.
     */
    @GetMapping("/summary")
    fun summaries(
        @RequestParam(defaultValue = "false") includeArchived: Boolean,
    ): List<MeasurementUnitSummaryDTO> {
        logger.info("Richiesta dell'elenco delle Measurement Unit (dissociate incluse: {})", includeArchived)
        return mus.summaries(includeArchived)
    }

    /** Il dettaglio con i sensori: e' quello che apre la pagina dei grafici. */
    @GetMapping("/{id}")
    fun one(@PathVariable id: Long): MeasurementUnitDTO =
        mus.get(id) ?: throw EntityNotFoundException("Measurement Unit $id non trovata")

    /**
     * Eventi e diagnostica di una MU, attraverso gli spostamenti.
     *
     * Non e' il sottoinsieme degli eventi di una CU: una MU che ha cambiato dispositivo
     * porta con se' la propria storia, ed e' il motivo per cui la riga dell'evento tiene un
     * riferimento diretto alla MU invece di farlo dedurre dal Local ID di allora.
     */
    @GetMapping("/{id}/events")
    fun events(@PathVariable id: Long): List<DeviceEventDTO> = mus.events(id)

    /**
     * Dissocia: la MU esce dall'elenco, niente viene cancellato.
     *
     * Se ricompare in una notifica di topologia torna visibile da sola: e' di nuovo in uso, e
     * tenerla nascosta mentre misura sarebbe una bugia.
     */
    @PostMapping("/{id}/dissociate")
    fun dissociate(@PathVariable id: Long): MeasurementUnitSummaryDTO {
        logger.info("Dissociazione della Measurement Unit {}", id)
        return mus.dissociate(id)
    }

    /**
     * Formatta: cancella la MU, i suoi sensori, le misure, gli eventi e gli allarmi.
     *
     * Riservata agli amministratori, perche' quello che se ne va e' lo storico metrologico.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    fun erase(@PathVariable id: Long) {
        logger.warn("Formattazione della Measurement Unit {}", id)
        mus.delete(id)
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
