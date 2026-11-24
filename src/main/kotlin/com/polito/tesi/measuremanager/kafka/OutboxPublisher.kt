package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.repositories.OutboxEventRepository
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Consegna gli eventi dell'outbox (PIANO_METROLOGIA 2.6).
 *
 * Uno alla volta e in ordine, aspettando la conferma del broker: se Kafka non risponde ci si
 * ferma al primo errore e si riprova al giro dopo, cosi' l'ordine resta quello in cui i
 * campioni sono nati. Una consegna ripetuta e' possibile (consegna avvenuta, conferma persa):
 * chi consuma `sample-converted` deve essere idempotente sull'id del frame.
 */
@Component
class OutboxPublisher(
        private val outbox: OutboxEventRepository,
        private val kafka: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(OutboxPublisher::class.java)

    @Scheduled(fixedDelay = 2_000L, initialDelay = 10_000L)
    @Transactional
    fun publish() {
        val pending = outbox.findTop200BySentAtIsNullOrderByIdAsc()
        for (event in pending) {
            try {
                kafka.send(event.topic, event.eventKey ?: "", event.payload).get(10, TimeUnit.SECONDS)
                event.sentAt = OffsetDateTime.now()
                event.lastError = null
            } catch (e: Exception) {
                event.attempts += 1
                event.lastError = (e.cause?.message ?: e.message)?.take(500)
                if (event.attempts == 1 || event.attempts % 100 == 0) {
                    log.warn(
                            "Outbox: evento {} su {} non consegnato ({} tentativi): {}",
                            event.id,
                            event.topic,
                            event.attempts,
                            event.lastError,
                    )
                }
                // Ci si ferma qui: spedire i successivi romperebbe l'ordine.
                break
            }
        }
    }

    /** Una settimana di consegnati basta per indagare; oltre e' solo spazio. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    fun purge() {
        val removed = outbox.deleteSentBefore(OffsetDateTime.now().minusDays(7))
        if (removed > 0) log.info("Outbox: {} eventi consegnati rimossi", removed)
    }
}
