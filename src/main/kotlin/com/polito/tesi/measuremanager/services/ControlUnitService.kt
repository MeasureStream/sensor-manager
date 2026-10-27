package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ControlUnitService {
    fun getControlUnit(id: Long): ControlUnitDTO?

    fun getAllControlUnits(
            name: String?,
    ): List<ControlUnitDTO>

    fun getAllControlUnitsPage(
            page: Pageable,
            name: String?,
    ): Page<ControlUnitDTO>

    fun claimControlUnit(hash: String): ControlUnitDTO

    /** Gli allarmi ricevuti da una CU, dal piu' recente. */
    fun getAlarms(controlUnitId: Long): List<AlarmDTO>

    /** Gli eventi di diagnostica ricevuti da una CU, dal piu' recente. */
    fun getEvents(controlUnitId: Long): List<DeviceEventDTO>

    /** Presa in carico di un singolo evento: lo toglie dagli aperti, non dallo storico. */
    fun acknowledgeEvent(controlUnitId: Long, eventId: Long): DeviceEventDTO

    /** Gli ultimi frame ricevuti da una CU, con l'esito della lettura. */
    fun getFrames(controlUnitId: Long): List<UplinkFrameDTO>

    /**
     * Presa in carico: i contatori tornano a zero e i frame restano, marcati come visti.
     * E' l'utente a decidere quando un problema e' stato guardato, non il server.
     */
    fun acknowledgeFrames(controlUnitId: Long): ControlUnitDTO

    /**
     * Chiede alla CU il controllo di sanita' delle MU (bit 0 di `Command1` nella 0x0A). La
     * risposta — una notifica di topologia 0x11 — arriva in modo asincrono.
     */
    fun requestMuCheck(controlUnitId: Long): ControlUnitDTO

    /** Chiede alla CU di risincronizzare l'orologio: Command1 bit 4. */
    fun requestTimeSync(controlUnitId: Long): ControlUnitDTO

    fun onJoinNotification(c: CuJoinNotification)

    fun onStatusUpdate(c: CuStatusUpdate)

    fun onSignalUpdate(dto: SignalQualityUpdate)

    fun update(
            id: Long,
            c: ControlUnitDTO,
    ): ControlUnitDTO

    fun delete(id: Long)

    fun sendPollingUpdate(c: CUConfigCommandDTO): CUConfigCommandDTO?

    fun sendSensorSamplingUpdate(command: CUConfigurationDTO): CUConfigurationDTO

    /**
     * Accende o spegne il ripristino automatico della configurazione per questa CU.
     * Lo stato si conserva sul dispositivo, non sulla sessione di chi guarda.
     */
    fun setAutoRestoreConfig(controlUnitId: Long, enabled: Boolean): ControlUnitDTO

    /**
     * La configurazione dei sensori come la prepara la schermata dedicata: periodi e funzioni
     * di elaborazione insieme, raggruppati per valore. Restituisce cosa e' partito davvero.
     */
    fun applySensorConfig(
            controlUnitId: Long,
            command: SensorConfigCommandSetDTO,
    ): SensorConfigResultDTO

    fun sendTransmissionCommand(command: CUTransmissionCommandDTO): CUTransmissionCommandDTO

    fun updateMetadata(id: Long, newName: String?, newSemanticLocation: String?): ControlUnitDTO

    fun updateMeasureConfig(dto: MeasureCUConfigRequest): MeasureCUConfigRequest

    fun onMeasuresUpdate(dto: CuMeasuresUpdate)
}
