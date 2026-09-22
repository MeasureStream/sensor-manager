package com.polito.tesi.measuremanager.template

import com.polito.tesi.measuremanager.entities.ConfigSnapshot
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.repositories.ConfigSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Istantanee di configurazione: cosa era attivo quando CFG_VER valeva N.
 *
 * Il protocollo dice che un report va letto con la configurazione dichiarata dal suo
 * CFG_VER. Finora il server conosceva solo quella corrente, quindi un report in ritardo di
 * una configurazione non era leggibile. Con l'istantanea lo diventa.
 */
@Service
class ConfigSnapshotService(
        private val snapshots: ConfigSnapshotRepository,
) {
    private val logger = LoggerFactory.getLogger(ConfigSnapshotService::class.java)

    /**
     * Fotografa la configurazione corrente e la lega al CFG_VER appena raggiunto.
     * Va chiamata **dopo** aver incrementato il contatore e prima di inviare il comando.
     */
    @Transactional
    fun take(cu: ControlUnit): ConfigSnapshot {
        var globalIndex = 0
        val slots =
                cu.measurementUnits.sortedBy { it.localId }.flatMap { mu ->
                    mu.sensors.sortedBy { it.sensorIndex }.map { sensor ->
                        mapOf(
                                "globalIndex" to globalIndex++,
                                "localId" to mu.localId,
                                "sensorId" to sensor.id,
                                "sensorIndex" to sensor.sensorIndex,
                                "modelName" to sensor.modelName,
                                "configurationMeasure" to sensor.configurationMeasure,
                                "samplingPeriod" to sensor.samplingPeriod,
                        )
                    }
                }

        val snapshot =
                ConfigSnapshot(
                        controlUnit = cu,
                        cfgVersion = cu.configVersion,
                        content = mapOf("slots" to slots),
                )
        logger.info("CU {}: istantanea della configurazione {} con {} slot",
                cu.id, cu.configVersion, slots.size)
        return snapshots.save(snapshot)
    }

    /** L'istantanea che corrisponde al CFG_VER dichiarato da un report, se esiste. */
    fun forWireVersion(controlUnitId: Long, wireVersion: Int): ConfigSnapshot? =
            snapshots.findByWireVersion(controlUnitId, wireVersion.toLong()).firstOrNull()

    fun history(controlUnitId: Long): List<ConfigSnapshot> =
            snapshots.findTop20ByControlUnit_IdOrderByCfgVersionDesc(controlUnitId)
}
