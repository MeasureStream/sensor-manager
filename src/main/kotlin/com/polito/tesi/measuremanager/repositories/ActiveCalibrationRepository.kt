package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ActiveCalibration
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ActiveCalibrationRepository : JpaRepository<ActiveCalibration, Long> {

    fun findByMuExtendedIdAndSensorIndexAndValidFrom(
            muExtendedId: Long,
            sensorIndex: Int,
            validFrom: OffsetDateTime,
    ): ActiveCalibration?

    /** Tutta la storia di un esemplare, dalla piu' recente. */
    fun findByMuExtendedIdAndSensorIndexOrderByValidFromDesc(
            muExtendedId: Long,
            sensorIndex: Int,
    ): List<ActiveCalibration>

    /** Quelle di una MU intera: servono al DTO della MU senza una query per sensore. */
    fun findByMuExtendedIdOrderByValidFromDesc(muExtendedId: Long): List<ActiveCalibration>

    fun findByMuExtendedIdAndSensorIndexAndRevokedAtIsNull(
            muExtendedId: Long,
            sensorIndex: Int,
    ): List<ActiveCalibration>
}
