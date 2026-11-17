package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.MeasurementUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository

/**
 * Quante MU e quanti sensori ha una CU.
 *
 * Il conteggio si chiede al database perche' `measurementUnits` e `sensors` sono collezioni
 * pigre: leggerne la dimensione su un elenco di CU sarebbe una query per CU piu' una per MU.
 */
interface FleetCountByControlUnit {
    val cuId: Long
    val units: Long
    val sensors: Long
}

@Repository
interface MeasurementUnitRepository : JpaRepository<MeasurementUnit, Long>, PagingAndSortingRepository<MeasurementUnit, Long> {
    fun findByIdAndUser_UserId(
        id: Long,
        ownerId: String,
    ): MeasurementUnit?

    fun findAllByExtendedIdAndUser_UserId(
        networkId: Long,
        ownerId: String,
    ): List<MeasurementUnit>

    fun findAllByExtendedIdAndUser_UserId(
        networkId: Long,
        ownerId: String,
        pageable: Pageable,
    ): Page<MeasurementUnit>

    fun findAllByUser_UserId(ownerId: String): List<MeasurementUnit>

    /** Le MU di un utente che non sono state dissociate: l'elenco della pagina «Mie MU». */
    fun findAllByUser_UserIdAndArchivedAtIsNull(ownerId: String): List<MeasurementUnit>

    /** Tutte quelle in elenco, per un amministratore. */
    fun findAllByArchivedAtIsNull(): List<MeasurementUnit>

    fun findAllByUser_UserId(
        ownerId: String,
        pageable: Pageable,
    ): Page<MeasurementUnit>


    fun findAllByExtendedId(networkId: Long): List<MeasurementUnit>

    fun findByExtendedIdAndUser_UserId(
        networkId: Long,
        ownerId: String,
    ): MeasurementUnit?

    // fun findByNetworkIdAndNode_OwnerId(networkId: Long, ownerId: String) : MeasurementUnit?
    fun findByExtendedId(networkId: Long): MeasurementUnit?

    fun findAllByExtendedId(
        networkId: Long,
        pageable: Pageable,
    ): Page<MeasurementUnit>




    /** Tutte le MU di un modello: serve a riempire gli slot quando il modello viene pubblicato. */
    fun findAllByModel(model: Int): List<MeasurementUnit>

    @Query("SELECT MAX(m.extendedId) FROM MeasurementUnit m")
    fun findMaxExtendedId(): Long?

    /**
     * MU e sensori di ogni CU dell'elenco. Il `left join` tiene dentro anche una MU senza
     * sensori censiti, che e' il caso di una topologia appena notificata.
     */
    @Query(
        """
        select mu.controlUnit.id as cuId, count(distinct mu.id) as units, count(s.id) as sensors
        from MeasurementUnit mu left join mu.sensors s
        where mu.controlUnit.id in :ids
        group by mu.controlUnit.id
        """
    )
    fun countByControlUnit(ids: Collection<Long>): List<FleetCountByControlUnit>
}
