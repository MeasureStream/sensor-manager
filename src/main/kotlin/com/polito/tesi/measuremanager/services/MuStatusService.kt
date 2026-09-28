package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.MuStatusUpdate
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.template.ProtocolService
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Stato delle MU (comando 0x12).
 *
 * E' l'unico canale da cui il server sa che una MU e' viva: il report dice che sono arrivati
 * dei byte, non che il dispositivo stia bene. Una MU che ha il bus I2C bloccato continua a
 * comparire nei report con le sue sentinelle, e senza questa parola di stato nessuno saprebbe
 * distinguere «nessun dato nuovo» da «guasto».
 *
 * I significati dei bit non si salvano: si risolvono a ogni lettura con il dizionario di
 * protocollo, cosi' un bit che domani acquista senso non obbliga a rileggere lo storico.
 */
@Service
class MuStatusService(
        private val cur: ControlUnitRepository,
        private val mur: MeasurementUnitRepository,
        private val protocol: ProtocolService,
) {
    private val log = LoggerFactory.getLogger(MuStatusService::class.java)

    @Transactional
    fun onMuStatus(dto: MuStatusUpdate) {
        val cu = cur.findByDevEui(dto.devEui)
        if (cu == null) {
            log.warn("Control Unit non trovata per DevEUI={}: stato MU ignorato", dto.devEui)
            return
        }

        // STATUS_SEQ e' progressivo: un salto significa che un cambiamento di stato non e'
        // mai arrivato, ed e' l'unico modo di accorgersene.
        cu.lastStatusSeq?.let { last ->
            val expected = (last + 1) and 0xFF
            if (dto.statusSeq != expected) {
                log.warn(
                        "DevEUI={}: sequenza di stato MU discontinua, atteso {} ricevuto {}: messaggi persi",
                        dto.devEui,
                        expected,
                        dto.statusSeq,
                )
            }
        }
        cu.lastStatusSeq = dto.statusSeq
        cur.save(cu)

        val now = OffsetDateTime.now()
        dto.entries.forEach { entry ->
            val mu = cu.measurementUnits.firstOrNull { it.localId == entry.localId }
            if (mu == null) {
                // La CU conosce una MU che il server non ha: la topologia e' cambiata e la
                // notifica 0x11 non e' ancora arrivata, oppure si e' persa.
                log.warn(
                        "DevEUI={}: stato della MU con Local ID {}, che non risulta censita",
                        dto.devEui,
                        entry.localId,
                )
                return@forEach
            }

            mu.statusWord = entry.statusWord
            mu.statusAt = now
            mur.save(mu)

            val flags = protocol.muStatusFlags(entry.statusWord)
            if (flags.isNotEmpty()) {
                log.info(
                        "MU {} (Local ID {}): {}",
                        mu.extendedId,
                        entry.localId,
                        flags.joinToString { it.description },
                )
            }
        }
    }
}
