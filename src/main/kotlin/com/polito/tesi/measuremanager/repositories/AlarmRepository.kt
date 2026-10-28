package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.SensorAlarm
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SensorAlarmRepository : JpaRepository<SensorAlarm, Long> {

    fun findTop100ByControlUnit_IdOrderByReceivedAtDesc(controlUnitId: Long): List<SensorAlarm>

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
