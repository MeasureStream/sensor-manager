package com.polito.tesi.measuremanager.dtos

data class CuJoinNotification(
        val devEui: Long,
        val deviceId: String,
        val muList: List<MuDescriptor>
)

/**
 * Una MU dichiarata dalla CU. [major] e' il MAJOR del modello, che arriva solo con la
 * notifica 0x11: con la 0x10 resta null e il server prende la versione piu' alta pubblicata.
 */
data class MuDescriptor(
        val extendedId: Long,
        val localId: Int,
        val model: Int,
        val major: Int? = null,
)

/**
 * Il poll: quello che la CU racconta di se' a ogni contatto.
 *
 * I campi da [configVersion] in giu' esistono solo nel formato v1.2 (0x0B a nove byte) e
 * restano null con i firmware precedenti: e' la differenza fra «non lo dichiara» e «vale
 * zero», e confonderle significherebbe credere che una CU sia disallineata quando non lo e'.
 */
data class CuStatusUpdate(
        val devEui: Long,
        val deviceId: String,
        val model: Int,
        val batteryLevel: Int,
        val ptx: Int,
        val acPowered: Boolean,
        val isCharging: Boolean,
        /** La parola di stato come e' arrivata, catena di estensione compresa. */
        val statusRaw: Int,
        /** CFG_VER dichiarato dalla CU: se non e' quello atteso, la configurazione non e' stata applicata. */
        val configVersion: Int? = null,
        /** ProtoVer: 0x12 per il protocollo v1.2. */
        val protocolVer: Int? = null,
        /** ALARM_SEQ corrente: dice se un messaggio di allarme non e' mai arrivato. */
        val alarmSeq: Int? = null,
)

/** Stato delle MU (comando 0x12): una parola di sedici bit per ogni MU dichiarata. */
data class MuStatusUpdate(
        val devEui: Long,
        val deviceId: String,
        val statusSeq: Int,
        val entries: List<MuStatusReport>,
)

data class MuStatusReport(val localId: Int, val statusWord: Int)

data class SignalQualityUpdate(
        val devEUI: String,
        val deviceId: String,
        val rssi: Int,
        val snr: Double,
        val dataRate: String,
        val airtime: String,
        val time: String,
        val spreadingFactor: Int,
        val bandwidth: Int,
        /**
         * Frame counter LoRaWAN dell'uplink (uplink_message.f_cnt nel JSON TTN). Default 0: TTN
         * omette il campo quando vale 0 e mantiene la compatibilità con messaggi prodotti da
         * versioni precedenti di kafka-stream.
         */
        val fCnt: Int = 0
)
