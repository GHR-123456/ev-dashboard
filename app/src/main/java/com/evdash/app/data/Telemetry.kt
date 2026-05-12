package com.evdash.app.data

enum class Gear(val label: String) {
    P("P"),
    R("R"),
    D1("D1"),
    D2("D2"),
    D3("D3"),
    BOOST("BOOST")
}

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DEMO
}

enum class Severity { INFO, WARN, CRITICAL }

data class ErrorCode(
    val code: String,
    val severity: Severity,
    val message: String
)

data class TelemetrySnapshot(
    val timestampMs: Long,
    val speedKmh: Float,
    val rpm: Int,
    val gear: Gear,
    val odometerKm: Float,
    val tripKm: Float,
    val socPercent: Float,
    val packVoltage: Float,
    val packCurrent: Float,
    val instantPowerKw: Float,
    val estimatedRangeKm: Float,
    val motorTempC: Float,
    val controllerTempC: Float,
    val errors: List<ErrorCode>,
    val connectionState: ConnectionState
) {
    companion object {
        fun empty(): TelemetrySnapshot = TelemetrySnapshot(
            timestampMs = 0L,
            speedKmh = 0f,
            rpm = 0,
            gear = Gear.P,
            odometerKm = 0f,
            tripKm = 0f,
            socPercent = 0f,
            packVoltage = 0f,
            packCurrent = 0f,
            instantPowerKw = 0f,
            estimatedRangeKm = 0f,
            motorTempC = 25f,
            controllerTempC = 25f,
            errors = emptyList(),
            connectionState = ConnectionState.DISCONNECTED
        )
    }
}
