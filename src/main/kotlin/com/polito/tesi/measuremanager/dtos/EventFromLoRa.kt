package com.polito.tesi.measuremanager.dtos

data class CuJoinNotification(
        val devEui: Long,
        val deviceId: String,
        val muList: List<MuDescriptor>
)

data class MuDescriptor(val extendedId: Long, val localId: Int, val model: Int)

data class CuStatusUpdate(
        val devEui: Long,
        val deviceId: String,
        val model: Int,
        val batteryLevel: Int,
        val ptx: Int,
        val acPowered: Boolean,
        val isCharging: Boolean,
        val statusRaw: Int
)

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
