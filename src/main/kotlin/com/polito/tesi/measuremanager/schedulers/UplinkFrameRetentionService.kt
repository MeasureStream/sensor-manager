package com.polito.tesi.measuremanager.schedulers

import com.polito.tesi.measuremanager.repositories.UplinkFrameRepository
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Ritenzione dei frame grezzi.
 *
 * Un anno, che e' la scelta presa quando la tabella e' nata: tiene insieme il confronto fra
 * stagioni e una dimensione prevedibile. Con una CU che trasmette ogni ora e un payload di
 * mezzo kilobyte si resta sotto i 5 MB l'anno per dispositivo; se la flotta cresce, il
 * parametro si abbassa senza toccare il codice.
 */
@Service
class UplinkFrameRetentionService(
        private val frames: UplinkFrameRepository,
        @Value("\${measurestream.frames.retention-days:365}") private val retentionDays: Long,
) {
    private val logger = LoggerFactory.getLogger(UplinkFrameRetentionService::class.java)

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    fun purgeOldFrames() {
        val threshold = OffsetDateTime.now().minusDays(retentionDays)
        val removed = frames.deleteReceivedBefore(threshold)
        if (removed > 0) {
            logger.info("Frame piu' vecchi di {} giorni rimossi: {}", retentionDays, removed)
        }
    }
}
