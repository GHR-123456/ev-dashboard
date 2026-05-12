package com.evdash.app.protocol

import com.evdash.app.data.ConnectionState
import com.evdash.app.data.ErrorCode
import com.evdash.app.data.Gear
import com.evdash.app.data.Severity
import com.evdash.app.data.TelemetrySnapshot
import java.util.UUID

/**
 * Xiaomi M365 / Ninebot 滑板车协议。
 *
 * 帧头 0x55 0xAA，长度在第 3 字节，地址 + 命令 + 数据 + 校验和（累加和）。
 */
class M365Protocol : ControllerProtocol {

    override val id = "m365"
    override val name = "M365 / 九号"

    override val serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    override val notifyUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    override val writeUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    override val requestPacket = buildRequest()
    override val pollIntervalMs = 500L

    private val frameBuffer = FrameBuffer(
        header = byteArrayOf(0x55.toByte(), 0xAA.toByte()),
        lengthExtractor = { arr, idx ->
            (arr.getOrNull(idx + 2)?.toInt()?.and(0xFF) ?: 0) + 4
        },
        minFrameSize = 6,
        maxFrameSize = 64
    )

    override fun parse(bytes: ByteArray): TelemetrySnapshot? {
        val frames = frameBuffer.feed(bytes)
        if (frames.isEmpty()) return null

        val frame = frames.last()
        if (frame.size < 10) return null

        val len = frame[2].toInt().and(0xFF)
        val addr = frame[3].toInt().and(0xFF)
        val cmd = frame[4].toInt().and(0xFF)
        val payload = frame.copyOfRange(5, 5 + len - 2)
        val checksum = frame[frame.size - 1]
        val calc = checksumSum(frame, 2, frame.size - 3)
        if (checksum != calc) return null

        if (addr != 0x20 || cmd != 0x65) return null
        if (payload.size < 12) return null

        val speedRaw = ((payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
        val speedKmh = speedRaw * 0.001f * 3.6f

        val voltageRaw = ((payload.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(3)?.toInt()?.and(0xFF) ?: 0)
        val voltageF = voltageRaw * 0.1f

        val currentRaw = ((payload.getOrNull(4)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(5)?.toInt()?.and(0xFF) ?: 0)
        val currentF = (currentRaw - 32768) * 0.01f

        val temp = payload.getOrNull(6)?.toInt()?.and(0xFF) ?: 0
        val soc = payload.getOrNull(7)?.toInt()?.and(0xFF) ?: 0

        val errors = buildList {
            if (temp > 65) add(ErrorCode("M01", Severity.WARN, "电池过温"))
        }

        return TelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            speedKmh = kotlin.math.abs(speedKmh).coerceIn(0f, 60f),
            rpm = (kotlin.math.abs(speedKmh) * 30f).toInt(),
            gear = Gear.D1,
            odometerKm = 0f,
            tripKm = 0f,
            socPercent = soc.toFloat().coerceIn(0f, 100f),
            packVoltage = voltageF,
            packCurrent = currentF,
            instantPowerKw = (voltageF * currentF / 1000f).coerceIn(-10f, 10f),
            estimatedRangeKm = soc * 0.3f,
            motorTempC = temp.toFloat(),
            controllerTempC = temp.toFloat(),
            errors = errors,
            connectionState = ConnectionState.CONNECTED
        )
    }

    private fun buildRequest(): ByteArray {
        val data = byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0x03, 0x20, 0x01, 0x00)
        return data + checksumSum(data, 2, data.size - 2)
    }
}
