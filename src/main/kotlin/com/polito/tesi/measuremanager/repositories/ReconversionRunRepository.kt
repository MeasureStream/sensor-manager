package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ReconversionMode
import com.polito.tesi.measuremanager.entities.ReconversionRun
import com.polito.tesi.measuremanager.entities.ReconversionState
import org.springframework.data.jpa.repository.JpaRepository

interface ReconversionRunRepository : JpaRepository<ReconversionRun, Long> {
    fun findFirstByStateInOrderByIdAsc(states: Collection<ReconversionState>): ReconversionRun?

    fun findByCalibrationIdAndModeAndRequestedBy(calibrationId: Long, mode: ReconversionMode, requestedBy: String): ReconversionRun?

    fun findAllByMuExtendedIdAndSensorIndexOrderByIdDesc(muExtendedId: Long, sensorIndex: Int): List<ReconversionRun>
}
