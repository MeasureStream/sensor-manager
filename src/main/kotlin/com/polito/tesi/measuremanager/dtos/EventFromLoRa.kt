package com.polito.tesi.measuremanager.dtos

data class CuJoinNotification(
    val devEui: Long,
    val muList: List<MuDescriptor>
)

data class MuDescriptor(
    val extendedId: Long,
    val localId: Int,
    val model: Int
)

data class CuStatusUpdate(
    val devEui: Long,
    val model: Int,
    val batteryLevel: Int,
    val statusRaw: Int
)
