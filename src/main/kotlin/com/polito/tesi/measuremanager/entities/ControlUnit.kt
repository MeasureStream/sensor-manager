package com.polito.tesi.measuremanager.entities

import jakarta.persistence.*
import jakarta.validation.constraints.*
import java.time.LocalDateTime
import org.springframework.data.geo.Point

@Entity
class ControlUnit {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) var id: Long = 0

    var model: Int = 0

    lateinit var deviceId: String

    @Column(unique = true, nullable = false) var devEui: Long = 0

    @NotBlank(message = "name is mandatory") lateinit var name: String

    @PositiveOrZero @Max(100) var remainingBattery: Double = 0.0

    var acPowered: Boolean = false
    var isCharging: Boolean = false

    @NegativeOrZero(message = "rssi must be negative") var rssi: Double = 0.0

    var location: Point? = null

    @ManyToOne var user: User? = null

    var status: Int = 0

    var dataRate: Int = 0
    var usedDC: Int = 0
    var hasGPS: Boolean = false
    var MaxMU: Int = 0

    @OneToMany(mappedBy = "controlUnit")
    var measurementUnits: MutableList<MeasurementUnit> = mutableListOf()

    @OneToMany(mappedBy = "controlUnit", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var signalQualities: MutableList<SignalQuality> = mutableListOf()

    /** Corrisponde a Setting1 (Byte 1). Gestito come Int per facilitare operazioni bitwise. */
    var setting1: Int = 0

    /** Corrisponde a P_TX (Byte 2). Potenza di trasmissione. */
    var transmissionPower: Int = 0

    /** Corrisponde a Delta T_Polling (Byte 3). Periodo tra due messaggi di stato. */
    var pollingInterval: Int = 0

    var transmissionInterval: Int = 0

    var semanticLocation: String = ""
    var bandwidth: Int = 0
    var spreadingFactor: Int = 0
    var codingRate: String = ""
    var frequency: Int = 0

    // data ultimo pacchetto arrivato
    var lastSeen: LocalDateTime? = null

    var usedDailyAirtime: Long = 0 // dato in ms

    var lastAirtime: Double = 0.0 // dato in secondi

    /**
     * Ultimo frame counter (f_cnt) LoRaWAN ricevuto da TTN per questa CU. Serve per i controlli di
     * continuità/integrità dei messaggi (salti di f_cnt = pacchetti persi; f_cnt più basso =
     * reset/rejoin della CU). null = nessun uplink ancora ricevuto.
     */
    var lastFCnt: Int? = null

    var configVersion: Long = 0

    /**
     * Report scartati perche' la CU dichiarava un CFG_VER diverso da quello atteso.
     * Si azzera al primo report allineato: e' il segno che il guasto e' rientrato.
     */
    var configMismatchCount: Long = 0

    /** Quando e' arrivato l'ultimo report scartato per disallineamento. */
    var lastConfigMismatchAt: java.time.OffsetDateTime? = null

    /** Ultimo CFG_VER dichiarato dalla CU: confrontato con [configVersion] dice di quanto e' fuori fase. */
    var lastReportedConfigVersion: Int? = null

    /**
     * Report scartati perche' non decodificabili: payload troncato o corrotto, base64 non
     * valido. Diverso da [configMismatchCount], che conta il disallineamento di versione.
     */
    var decodeFailureCount: Long = 0

    /**
     * CMD_SEQ: numero dell'ultimo comando di configurazione inviato, nel prologo di blocco.
     * Serve alla CU per riconoscere una ritrasmissione e non applicarla due volte. Viaggia su
     * un byte, quindi va in wrap a 255 come CFG_VER.
     */
    var cmdSeq: Int = 0

    /**
     * ALARM_SEQ ed EVENT_SEQ dell'ultimo messaggio ricevuto. Sono progressivi: confrontarli
     * con quello che arriva e' l'unico modo che il server ha di accorgersi di un allarme o di
     * un evento perso per strada.
     */
    var lastAlarmSeq: Int? = null

    var lastEventSeq: Int? = null

    /**
     * ALARM_SEQ dichiarato dalla CU nel poll 0x0B. Confrontato con [lastAlarmSeq] dice se un
     * messaggio di allarme non e' mai arrivato: il poll lo rivela anche quando il 0xA0 si e'
     * perso del tutto, che e' il caso in cui accorgersene serve davvero.
     */
    var reportedAlarmSeq: Int? = null

    /** STATUS_SEQ dell'ultimo comando 0x12 ricevuto, per lo stesso controllo di continuita'. */
    var lastStatusSeq: Int? = null

    /**
     * ProtoVer dichiarato nel poll 0x0B: 0x12 per il protocollo v1.2. Null finche' la CU
     * non lo dichiara, cioe' finche' monta un firmware precedente. E' la condizione per
     * dismettere le vecchie FPort: nessuna si tocca finche' esiste una CU senza questo campo.
     */
    var protocolVer: Int? = null

    /**
     * I due byte Status del poll, come sono arrivati. I significati non si salvano: si
     * risolvono a ogni lettura con il dizionario di protocollo, cosi' un bit che domani
     * acquista senso non richiede di rileggere lo storico.
     */
    var statusWord: Int? = null

    var statusAt: java.time.OffsetDateTime? = null
}
