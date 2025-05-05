package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.DCC
import org.springframework.data.jpa.repository.JpaRepository

interface DCCRepository : JpaRepository<DCC,Long> {
}