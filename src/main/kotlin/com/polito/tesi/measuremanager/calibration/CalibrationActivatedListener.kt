package com.polito.tesi.measuremanager.calibration

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.stereotype.Component

/**
 * Il consumer di `calibration-activated` (PIANO_METROLOGIA 1.12).
 *
 * A ogni avvio rilegge il topic **da capo**: e' compattato, quindi da capo vuol dire
 * l'ultima attivazione di ogni esemplare, e la tabella `active_calibration` si ricostruisce
 * anche se e' stata svuotata. L'applicazione e' idempotente — inserisce o aggiorna sulla
 * chiave (esemplare, inizio validita') — quindi rileggere non duplica niente.
 */
@Component
class CalibrationActivatedListener(
        private val service: ActiveCalibrationService,
        private val objectMapper: ObjectMapper,
) : ConsumerSeekAware {
    private val log = LoggerFactory.getLogger(CalibrationActivatedListener::class.java)

    override fun onPartitionsAssigned(
            assignments: MutableMap<TopicPartition, Long>,
            callback: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        callback.seekToBeginning(assignments.keys)
    }

    @KafkaListener(
            topics = [CalibrationActivatedEvent.TOPIC],
            groupId = "sensor-manager-calibration-activated",
            containerFactory = "calibrationActivatedFactory",
    )
    fun consume(record: ConsumerRecord<String?, String?>) {
        try {
            val event = record.value()?.let { objectMapper.readValue(it, CalibrationActivatedEvent::class.java) }
            service.apply(record.key(), event)
        } catch (e: Exception) {
            // Un messaggio illeggibile non deve fermare gli altri esemplari: si registra e si
            // va avanti. Il campione di quell'esemplare restera' convertito con la nominale,
            // e il flag CAL_INSTANCE assente lo dichiara.
            log.error("Attivazione non applicata (chiave {}): {}", record.key(), e.message, e)
        }
    }
}
