package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.DeviceEventDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitSummaryDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface MeasurementUnitService {
    fun get(id: Long): MeasurementUnitDTO?

    /**
     * L'elenco della pagina «Mie MU»: tutte quelle che un utente ha usato nel tempo, montate
     * adesso o no. Un amministratore le vede tutte, comprese quelle ancora senza proprietario
     * perche' la loro CU non e' mai stata rivendicata.
     */
    fun summaries(includeArchived: Boolean = false): List<MeasurementUnitSummaryDTO>

    /** Eventi e diagnostica di una MU, attraverso gli spostamenti fra CU diverse. */
    fun events(id: Long): List<DeviceEventDTO>

    /** Fuori dall'elenco, con tutti i dati conservati. */
    fun dissociate(id: Long): MeasurementUnitSummaryDTO

    fun getAll(
        extendedId: Long?,
        controlUnitDevEUI: Long?,
        controlUnitName: String?,
    ): List<MeasurementUnitDTO>

    fun getAllPage(
        page: Pageable,
        extendedId: Long?,
        controlUnitDevEUI: Long?,
        controlUnitName: String?,
    ): Page<MeasurementUnitDTO>



    fun update(
        id: Long,
        m: MeasurementUnitDTO,
    ): MeasurementUnitDTO

    fun delete(id: Long)



}
