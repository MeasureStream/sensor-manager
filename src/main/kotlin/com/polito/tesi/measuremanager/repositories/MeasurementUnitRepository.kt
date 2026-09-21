package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.MeasurementUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository

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




    @Query("SELECT MAX(m.extendedId) FROM MeasurementUnit m")
    fun findMaxExtendedId(): Long?
}
