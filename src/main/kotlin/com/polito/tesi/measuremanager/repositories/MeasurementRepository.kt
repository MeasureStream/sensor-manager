package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.Measurement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository interface MeasurementRepository : JpaRepository<Measurement, Long> {}
