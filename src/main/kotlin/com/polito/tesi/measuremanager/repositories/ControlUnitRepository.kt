package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ControlUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface ControlUnitRepository : JpaRepository<ControlUnit, Long>, PagingAndSortingRepository<ControlUnit, Long> {


    fun findAllByNameAndUser_UserId(
        name: String,
        ownerId: String,
    ): List<ControlUnit>

    fun findAllByUser_UserId(ownerId: String): List<ControlUnit>

    fun findByIdAndUser_UserId(
        id: Long,
        ownerId: String,
    ): ControlUnit?



    fun findAllByNameAndUser_UserId(
        name: String,
        ownerId: String,
        pageable: Pageable,
    ): Page<ControlUnit>

    fun findAllByUser_UserId(
        ownerId: String,
        pageable: Pageable,
    ): Page<ControlUnit>


    fun findAllByName(name: String): List<ControlUnit>

    fun findByDevEui(networkId: Long): ControlUnit?




    fun findAllByName(
        name: String,
        pageable: Pageable,
    ): Page<ControlUnit>

    @Modifying
    @Transactional
    @Query("UPDATE ControlUnit c SET c.usedDailyAirtime = 0")
    fun resetDailyAirtime(): Int

}
