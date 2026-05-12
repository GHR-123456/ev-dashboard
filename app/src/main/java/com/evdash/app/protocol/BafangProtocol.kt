package com.evdash.app.protocol

import com.evdash.app.data.ConnectionState
import com.evdash.app.data.Gear
import com.evdash.app.data.TelemetrySnapshot
import java.util.UUID

/**
 * Bafang (八方) 电机控制器协议。
 *
 * 帧头 0x11 0x20，固定长度 13 字节帧，和校验。
 * 八方通常主动上报，不需要轮询。
 */
class BafangProtocol : ControllerProtocol {

    override val id = "bafang"
    override val name = "Bafang (八方)"

    override val serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    override val notifyUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    override val writeUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    override val pollIntervalMs = 0L

    private val frameBuffer = FrameBuffer(
        header = byteArrayOf(0x11, 0x20),
        lengthExtractor = { _, _ -> 13 },
        minFrameSize = 13,
        maxFrameSize = 13
    )

    override fun parse(bytes: ByteArray): TelemetrySnapshot? {
        val frames = frameBuffer.feed(bytes)
        if (frames.isEmpty()) return null

        val frame = frames.last()
        if (frame.size != 13) return null

        val checksum = frame.last()
        val calc = checksumSum(frame, 0, frame.size - 1)
        if (checksum != calc) return null

        val payload = frame.copyOfRange(2, frame.size - 1)

        val voltage = (payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0)
        val currentRaw = (payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
        val current = if (currentRaw > 127) currentRaw - 256 else currentRaw
        val speed = (payload.getOrNull(2)?.toInt()?.and(0xFF) ?: 0)
        val temp = (payload.getOrNull(3)?.toInt()?.and(0xFF) ?: 0)

        val voltageF = voltage * 0.5f
        val currentF = current * 0.1f
        val speedKmh = speed * 0.5f

        return TelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh.coerceIn(0f, 120f),
            rpm = (speedKmh * 75f).toInt(),
            gear = Gear.D1,
            odometerKm = 0f,
            tripKm = 0f,
            socPercent = ((voltageF - 36f) / (54f - 36f) * 100f).coerceIn(0f, 100f),
            packVoltage = voltageF,
            packCurrent = currentF,
            instantPowerKw = (voltageF * currentF / 1000f).coerceIn(-20f, 20f),
            estimatedRangeKm = 0f,
            motorTempC = temp.toFloat(),
            controllerTempC = temp.toFloat(),
            errors = emptyList(),
            connectionState = ConnectionState.CONNECTED
        )
    }
}
