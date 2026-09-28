package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * Un evento ricevuto sulla FPort 0xA2: un fatto accaduto al dispositivo.
 *
 * Non ha un rientro e non provoca mai una trasmissione: viaggia con il primo uplink gia'
 * previsto, quindi arriva con un'eta' ([ageRaw]) e non con un istante. L'istante lo ricava
 * il server, e porta con se' l'incertezza della fascia di codifica.
 */
@Entity
@Table(name = "device_event")
class DeviceEvent(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "control_unit_id")
        var controlUnit: ControlUnit? = null,
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sensor_id") var sensor: Sensor? = null,
        @Column(name = "received_at", nullable = false)
        var receivedAt: OffsetDateTime = OffsetDateTime.now(),
        @Column(name = "event_seq") var eventSeq: Int? = null,
        /** Local ID della MU che ha generato l'evento; 0 indica la CU stessa. */
        @Column(nullable = false) var source: Int = 0,
        /** 255 (0xFF) quando l'evento riguarda il dispositivo e non un suo canale. */
        @Column(name = "sensor_index", nullable = false) var sensorIndex: Int = 0xFF,
        @Column(nullable = false) var code: Int = 0,
        @Column var param: Int? = null,
        @Column(name = "age_raw") var ageRaw: Int? = null,
        @Column(name = "occurred_at") var occurredAt: OffsetDateTime? = null,
        /** Il passo della fascia di codifica: e' un'incertezza, non un dettaglio. */
        @Column(name = "age_uncertainty_seconds") var ageUncertaintySeconds: Int? = null,
        @Column(columnDefinition = "text") var description: String? = null,
        @Column(name = "acknowledged_at") var acknowledgedAt: OffsetDateTime? = null,
)
