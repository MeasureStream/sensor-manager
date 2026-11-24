package com.polito.tesi.measuremanager.calibration

import com.polito.tesi.measuremanager.entities.ActiveCalibration
import com.polito.tesi.measuremanager.repositories.ActiveCalibrationRepository
import com.polito.tesi.measuremanager.template.ConversionFunction
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** La taratura scelta per un campione, gia' pronta per la conversione. */
data class ResolvedCalibration(
        val record: ActiveCalibration,
        val function: ConversionFunction,
        val expired: Boolean,
) {
    fun outOfRange(value: Double?): Boolean {
        if (value == null) return false
        val min = record.calibratedMin
        val max = record.calibratedMax
        return (min != null && value < min) || (max != null && value > max)
    }
}

/**
 * Le tarature attive degli esemplari: le scrive il consumer del topic, le legge la
 * decodifica di ogni report.
 *
 * La decodifica e' il percorso caldo — ogni report, ogni sensore — quindi la storia di ogni
 * esemplare sta in una cache in memoria, invalidata a ogni scrittura. Gli esemplari tarati
 * sono pochi e le tarature per esemplare una all'anno: la cache resta piccola.
 */
@Service
class ActiveCalibrationService(
        private val repository: ActiveCalibrationRepository,
        private val reconversions: ReconversionService,
) {
    private val log = LoggerFactory.getLogger(ActiveCalibrationService::class.java)

    private val cache = ConcurrentHashMap<Pair<Long, Int>, List<ActiveCalibration>>()

    /**
     * La taratura in uso per un esemplare all'istante [at]: la piu' recente fra quelle
     * iniziate e non revocate. Una taratura scaduta resta in uso — e' ancora la funzione
     * migliore che si conosca per quello strumento — ma il flag CAL_EXPIRED lo dichiara.
     */
    fun resolve(muExtendedId: Long, sensorIndex: Int, at: OffsetDateTime): ResolvedCalibration? {
        val record = history(muExtendedId, sensorIndex).firstOrNull { it.appliesAt(at) } ?: return null
        val function = ConversionFunction.of(record.function)
        if (function == null) {
            log.warn(
                    "Taratura {} dell'esemplare {}:{} senza una formula leggibile: si usa la nominale",
                    record.certificateId,
                    muExtendedId,
                    sensorIndex,
            )
            return null
        }
        return ResolvedCalibration(record, function, record.expiredAt(at))
    }

    /** La storia di un esemplare, dalla piu' recente. */
    fun history(muExtendedId: Long, sensorIndex: Int): List<ActiveCalibration> =
            cache.computeIfAbsent(muExtendedId to sensorIndex) {
                repository.findByMuExtendedIdAndSensorIndexOrderByValidFromDesc(muExtendedId, sensorIndex)
            }

    /** Applica un messaggio del topic: un'attivazione, o un tombstone se [event] e' null. */
    @Transactional
    fun apply(key: String?, event: CalibrationActivatedEvent?) {
        if (event == null) {
            val (euid, index) =
                    CalibrationActivatedEvent.parseKey(key)
                            ?: run {
                                log.warn("Tombstone con una chiave non leggibile: {}", key)
                                return
                            }
            revoke(euid, index)
            return
        }

        val existing =
                repository.findByMuExtendedIdAndSensorIndexAndValidFrom(
                        event.muExtendedId,
                        event.sensorIndex,
                        event.validFrom,
                )
        val record =
                existing
                        ?: ActiveCalibration(
                                muExtendedId = event.muExtendedId,
                                sensorIndex = event.sensorIndex,
                                certificateId = event.certificateId,
                                function = event.function,
                                validFrom = event.validFrom,
                        )

        record.calibratedItemId = event.calibratedItemId
        record.certificateId = event.certificateId
        record.certificateNumber = event.certificateNumber
        record.revision = event.revision
        record.function = event.function
        record.covariance = event.covariance
        record.calibratedMin = event.calibratedRange?.min
        record.calibratedMax = event.calibratedRange?.max
        record.validUntil = event.validUntil
        record.blockACrcExpected = event.blockACrc
        record.fingerprint = event.fingerprint
        // Un'attivazione che arriva di nuovo (riletta dal topic, o ripubblicata dopo una
        // revoca) e' di nuovo in vigore.
        record.revokedAt = null
        record.receivedAt = OffsetDateTime.now()
        val saved = repository.save(record)
        cache.remove(event.muExtendedId to event.sensorIndex)
        // La conoscenza nuova vale anche all'indietro: riconversioni GAP e DRIFT, se servono.
        reconversions.planAfterActivation(event, saved)

        log.info(
                "Taratura attiva per {}: certificato {} rev. {} dal {}",
                event.key(),
                event.certificateNumber ?: event.certificateId,
                event.revision,
                event.validFrom,
        )
    }

    private fun revoke(muExtendedId: Long, sensorIndex: Int) {
        val now = OffsetDateTime.now()
        val active = repository.findByMuExtendedIdAndSensorIndexAndRevokedAtIsNull(muExtendedId, sensorIndex)
        active.forEach { it.revokedAt = now }
        repository.saveAll(active)
        cache.remove(muExtendedId to sensorIndex)
        log.info("Taratura revocata per {}:{} ({} righe)", muExtendedId, sensorIndex, active.size)
    }
}
