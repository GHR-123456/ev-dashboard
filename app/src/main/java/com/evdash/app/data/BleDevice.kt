package com.evdash.app.data

data class BleDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMs: Long
)
