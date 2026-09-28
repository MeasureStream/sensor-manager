package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ConfigSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ConfigSnapshotRepository : JpaRepository<ConfigSnapshot, Long> {

    /**
     * L'istantanea di una CU il cui CFG_VER, ridotto al byte che viaggia sul filo, coincide
     * con quello dichiarato dal report. Se il contatore ha superato 255 piu' volte possono
     * esistere piu' candidate: si prende la piu' recente, che e' l'unica plausibile.
     */
    @Query(
            """
            select s from ConfigSnapshot s
            where s.controlUnit.id = :controlUnitId and mod(s.cfgVersion, 256) = :wireVersion
            order by s.cfgVersion desc
            """
    )
    fun findByWireVersion(controlUnitId: Long, wireVersion: Long): List<ConfigSnapshot>

    fun findTop20ByControlUnit_IdOrderByCfgVersionDesc(controlUnitId: Long): List<ConfigSnapshot>
}
