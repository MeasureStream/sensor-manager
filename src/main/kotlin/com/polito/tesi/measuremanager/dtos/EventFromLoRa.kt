package com.polito.tesi.measuremanager.dtos

data class CuJoinNotification(
    val devEui: Long,
    val deviceId: String,
    val muList: List<MuDescriptor>
)

data class MuDescriptor(
    val extendedId: Long,
    val localId: Int,
    val model: Int
)

data class CuStatusUpdate(
    val devEui: Long,
    val deviceId: String,
    val model: Int,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val statusRaw: Int
)

data class SignalQualityUpdate(
    val devEUI: String,
    val deviceId: String,
    val rssi: Int,
    val dataRate: String,
    val airtime: String,
    val time: String,
    val spreadingFactor: Int,
    val bandwidth : Int
)
