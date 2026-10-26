package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

/** Chi ha osservato un evento. Il campo separa anche gli spazi dei codici. */
object EventOrigin {
    /** Dichiarato dal dispositivo sulla FPort 0xA2. */
    const val DEVICE = "device"
    /** Letto dal server nella parola di stato del poll, sul fronte di salita di un bit. */
    const val STATUS = "status"
    /** Osservato dal server: nessun bit lo racconta, ma e' accaduto. */
    const val SERVER = "server"
}

/** I codici degli eventi che il server osserva da se'. Piccolo elenco, per scelta. */
object ServerEventCode {
    /**
     * Il comando inviato non risulta applicato: la CU dichiara un CFG_VER diverso da quello
     * atteso e un CMD_SEQ che non e' quello spedito. Si chiude da solo quando i conti tornano.
     */
    const val CONFIG_NOT_APPLIED = 1

    /** La configurazione e' stata rimandata da sola dopo un riavvio del dispositivo. */
    const val CONFIG_RESTORED = 2

    /** Il ripristino automatico non e' stato fatto: la mappa delle MU non e' piu' la stessa. */
    const val RESTORE_SKIPPED = 3
}

/**
 * Un fatto accaduto al dispositivo.
 *
 * Nasce in tre modi, che [origin] distingue: dichiarato dal dispositivo sulla FPort 0xA2,
 * letto dal server nella parola di stato del poll, oppure osservato dal server confrontando
 * cio' che ha inviato con cio' che la CU risponde.
 *
 * Un evento della 0xA2 non ha un rientro e non provoca mai una trasmissione: viaggia con il
 * primo uplink gia' previsto, quindi arriva con un'eta' ([ageRaw]) e non con un istante.
 * L'istante lo ricava il server, e porta con se' l'incertezza della fascia di codifica.
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
        /** Uno fra i valori di [EventOrigin]: separa anche gli spazi dei codici. */
        @Column(nullable = false) var origin: String = EventOrigin.DEVICE,
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
