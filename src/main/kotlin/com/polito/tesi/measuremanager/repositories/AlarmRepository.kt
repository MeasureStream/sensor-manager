package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.SensorAlarm
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Quanti avvisi aperti ha una MU.
 *
 * Una query sola per tutto l'elenco invece di una per riga: la pagina «Mie MU» mostra una
 * flotta, e un conteggio per riga sarebbe il modo classico di renderla lenta appena la
 * flotta cresce.
 */
interface OpenCountByUnit {
    val muId: Long
    val total: Long
}

/** Lo stesso conteggio raggruppato per CU: la riga della pagina «Mie CU». */
interface OpenCountByControlUnit {
    val cuId: Long
    val total: Long
}

@Repository
interface SensorAlarmRepository : JpaRepository<SensorAlarm, Long> {

    fun findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId: Long): List<SensorAlarm>

    @Query(
            """
            select a.measurementUnit.id as muId, count(a) as total
            from SensorAlarm a
            where a.measurementUnit.id in :ids and a.cleared = false and a.acknowledgedAt is null
            group by a.measurementUnit.id
            """
    )
    fun countOpenByMeasurementUnit(ids: Collection<Long>): List<OpenCountByUnit>

    /**
     * Gli allarmi aperti di ogni CU dell'elenco, in una query.
     *
     * E' la stessa regola di [countByControlUnit_IdAndClearedFalseAndAcknowledgedAtIsNull] —
     * scattato, non rientrato, non preso in carico — chiesta per tutta la flotta in un colpo
     * invece di una CU alla volta.
     */
    @Query(
            """
            select a.controlUnit.id as cuId, count(a) as total
            from SensorAlarm a
            where a.controlUnit.id in :ids and a.cleared = false and a.acknowledgedAt is null
            group by a.controlUnit.id
            """
    )
    fun countOpenByControlUnit(ids: Collection<Long>): List<OpenCountByControlUnit>

    fun deleteByMeasurementUnit_Id(measurementUnitId: Long): Long

    fun countByControlUnit_IdAndClearedFalseAndAcknowledgedAtIsNull(controlUnitId: Long): Long

    @Modifying
    @Query(
            """
            update SensorAlarm a set a.acknowledgedAt = :now
            where a.controlUnit.id = :controlUnitId and a.acknowledgedAt is null
            """
    )
    fun acknowledgeAll(controlUnitId: Long, now: OffsetDateTime): Int
}

@Repository
interface DeviceEventRepository : JpaRepository<DeviceEvent, Long> {

    fun findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId: Long): List<DeviceEvent>

    /**
     * Lo storico di una MU, attraverso gli spostamenti.
     *
     * Il limite e' alto perche' questa e' la tabella che si esporta: cinquecento righe sono
     * piu' di quante se ne leggano, e meno di quante ne servano per rendere pesante la
     * pagina. Oltre, la domanda giusta e' un filtro per data, non piu' righe.
     */
    fun findTop500ByMeasurementUnit_IdOrderByReceivedAtDesc(measurementUnitId: Long): List<DeviceEvent>

    @Query(
            """
            select e.measurementUnit.id as muId, count(e) as total
            from DeviceEvent e
            where e.measurementUnit.id in :ids and e.acknowledgedAt is null
            group by e.measurementUnit.id
            """
    )
    fun countOpenByMeasurementUnit(ids: Collection<Long>): List<OpenCountByUnit>

    /** Gli avvisi non ancora presi in carico di ogni CU dell'elenco, in una query. */
    @Query(
            """
            select e.controlUnit.id as cuId, count(e) as total
            from DeviceEvent e
            where e.controlUnit.id in :ids and e.acknowledgedAt is null
            group by e.controlUnit.id
            """
    )
    fun countOpenByControlUnit(ids: Collection<Long>): List<OpenCountByControlUnit>

    fun deleteByMeasurementUnit_Id(measurementUnitId: Long): Long

    /**
     * Gli eventi ancora aperti di un certo tipo.
     *
     * E' la domanda che decide se registrare: un bit di stato resta alzato per ore e il poll
     * passa ogni minuto, quindi senza questo controllo la stessa topologia cambiata
     * diventerebbe centinaia di righe. Finche' l'utente non prende in carico quella che c'e',
     * non se ne scrivono altre; quando l'ha presa in carico e il fatto si ripete, ne nasce
     * una nuova, ed e' giusto che sia cosi'.
     */
    fun findByControlUnit_IdAndOriginAndCodeAndAcknowledgedAtIsNull(
            controlUnitId: Long,
            origin: String,
            code: Int,
    ): List<DeviceEvent>

    /**
     * L'avviso aperto per quel codice **su quel canale**.
     *
     * Gli eventi del dispositivo hanno un soggetto: lo stesso codice su due sensori diversi
     * sono due fatti, e unirli perderebbe quale dei due e' guasto. Lo stesso codice sullo
     * stesso canale, invece, e' il medesimo fatto che dura, e di quello si tiene una riga
     * sola. L'interfaccia poi riunisce in un badge solo i testi uguali, ma le righe restano
     * distinte nello storico, dove il dettaglio serve.
     */
    fun findFirstByControlUnit_IdAndOriginAndCodeAndSourceAndSensorIndexAndAcknowledgedAtIsNull(
            controlUnitId: Long,
            origin: String,
            code: Int,
            source: Int,
            sensorIndex: Int,
    ): DeviceEvent?

    @Modifying
    @Query(
            """
            update DeviceEvent e set e.acknowledgedAt = :now
            where e.controlUnit.id = :controlUnitId and e.acknowledgedAt is null
            """
    )
    fun acknowledgeAll(controlUnitId: Long, now: OffsetDateTime): Int
}
