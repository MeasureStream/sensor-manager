package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Un allarme ricevuto sulla FPort 0xA0.
 *
 * Resta latchato finche' non arriva il rientro: la riga con [cleared] a true e' quella che
 * chiude la condizione, e porta lo stesso indirizzo di canale di quella che l'aveva aperta.
 */
@Entity
@Table(name = "sensor_alarm")
class SensorAlarm(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "control_unit_id")
        var controlUnit: ControlUnit? = null,
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sensor_id") var sensor: Sensor? = null,
        @Column(name = "received_at", nullable = false)
        var receivedAt: OffsetDateTime = OffsetDateTime.now(),
        @Column(name = "alarm_seq") var alarmSeq: Int? = null,
        @Column(name = "local_id", nullable = false) var localId: Int = 0,
        @Column(name = "sensor_index", nullable = false) var sensorIndex: Int = 0,
        /** Bit EVT: false se la condizione si e' attivata, true se e' rientrata. */
        @Column(nullable = false) var cleared: Boolean = false,
        /** La parola di allarme completa, catena di estensione inclusa. */
        @Column(name = "alarm_word", nullable = false) var alarmWord: Long = 0,
        /** Le condizioni riconosciute, gia' risolte in testo. */
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        var conditions: List<Map<String, Any?>>? = null,
        @Column(name = "raw_value") var rawValue: Int? = null,
        @Column var value: Double? = null,
        @Column(name = "acknowledged_at") var acknowledgedAt: OffsetDateTime? = null,
)
