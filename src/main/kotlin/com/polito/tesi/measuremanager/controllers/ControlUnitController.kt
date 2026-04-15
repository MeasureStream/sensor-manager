package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.CUConfigCommandDTO
import com.polito.tesi.measuremanager.dtos.CUConfigurationDTO
import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.CUTransmissionCommandDTO
import com.polito.tesi.measuremanager.services.ControlUnitService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/API/controlunits")
class ControlUnitController(
    private val cs: ControlUnitService,
) {
    // Logger istanziato per questa classe
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/", "")
    fun get(@RequestParam name: String?): List<ControlUnitDTO> {
        log.info("Richiesta GET per le Control Units (filtro nome: {})", name ?: "nessuno")
        return cs.getAllControlUnits(name = name)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PutMapping("/", "")
    fun updateCU(@Valid @RequestBody cu: ControlUnitDTO): ControlUnitDTO {
        log.info("Richiesta PUT: aggiornamento configurazione per CU ID {}", cu.id)
        return cs.update(cu.id, cu)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/", "")
    fun deleteCU(@RequestParam id: Long) {
        log.warn("Richiesta DELETE per CU ID {}", id)
        cs.delete(id)
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/polling", "/polling/")
    fun configureCU(@RequestBody command: CUConfigCommandDTO) {
        log.info("Ricevuto comando POLLING per EUI: {}", command.devEui)
        log.debug("Dettagli comando polling: {}", command)

        try {
            cs.sendPollingUpdate(command)
            log.info("Comando polling inviato al service con successo")
        } catch (e: Exception) {
            log.error("Errore durante la configurazione polling per {}: {}", command.devEui, e.message)
            throw e
        }
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/sensors-config")
    fun configureSensors(@RequestBody command: CUConfigurationDTO) {
        log.info("Ricevuta configurazione campionamento sensori per EUI: {}", command.devEui)
        log.debug("Dati campionamento: {}", command)

        try {
            cs.sendSensorSamplingUpdate(command)
            log.info("Configurazione sensori processata correttamente")
        } catch (e: Exception) {
            log.error("Errore configurazione sensori per {}: {}", command.devEui, e.message)
            throw e
        }
    }

    /**
     * ENDPOINT: START/STOP TRASMISSIONE
     * Gestisce l'avvio e l'arresto della sessione live dei sensori
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/transmission", "/transmission/")
    fun controlTransmission(@RequestBody command: CUTransmissionCommandDTO) {
        val action = if (command.transmissionIndex == 0) "STOP" else "START"

        log.info("Comando TRASMISSIONE [{}]: Ricevuto per EUI: {}", action, command.devEui)
        log.debug("Indice di trasmissione selezionato: {}", command.transmissionIndex)

        try {
            cs.sendTransmissionCommand(command)
            log.info("Comando {} inviato correttamente alla Control Unit", action)
        } catch (e: Exception) {
            log.error("Fallimento comando trasmissione per {}: {}", command.devEui, e.message)
            throw e
        }
    }
}
