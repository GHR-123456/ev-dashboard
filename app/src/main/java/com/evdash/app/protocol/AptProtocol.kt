package com.evdash.app.protocol

import com.evdash.app.data.ConnectionState
import com.evdash.app.data.ErrorCode
import com.evdash.app.data.Gear
import com.evdash.app.data.Severity
import com.evdash.app.data.TelemetrySnapshot
import java.util.UUID

/**
 * APT (智科) 控制器协议。
 *
 * 帧头 0xAA 0x55，长度在第 3 字节，校验为和校验取反。
 * 数据包含电压、电流、转速、档位、温度等。
 */
class AptProtocol : ControllerProtocol {

    override val id = "apt"
    override val name = "APT (智科)"

    override val serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    override val notifyUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    override val writeUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    override val requestPacket = buildRequest()
    override val pollIntervalMs = 200L

    private val frameBuffer = FrameBuffer(
        header = byteArrayOf(0xAA.toByte(), 0x55.toByte()),
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

        val cmd = frame.getOrNull(3)?.toInt()?.and(0xFF) ?: return null
        if (cmd != 0x01) return null

        val payload = frame.copyOfRange(4, frame.size - 1)
        val checksum = frame.last()
        if (checksum != checksumSumInv(frame, 0, frame.size - 1)) return null

        val voltage = ((payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
        val current = ((payload.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(3)?.toInt()?.and(0xFF) ?: 0)
        val rpm = ((payload.getOrNull(4)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(5)?.toInt()?.and(0xFF) ?: 0)
        val controllerTemp = payload.getOrNull(6)?.toInt()?.and(0xFF) ?: 0
        val motorTemp = payload.getOrNull(7)?.toInt()?.and(0xFF) ?: 0
        val gearRaw = payload.getOrNull(8)?.toInt()?.and(0xFF) ?: 0

        val voltageF = voltage * 0.1f
        val currentF = (current - 32768) * 0.1f
        val rpmI = rpm.toInt()
        val speedKmh = rpmI * 0.05f

        val gear = when (gearRaw) {
            0x00 -> Gear.P
            0x01 -> Gear.D1
            0x02 -> Gear.D2
            0x03 -> Gear.D3
            0x04 -> Gear.BOOST
            else -> Gear.D1
        }

        val errors = buildList {
            if (controllerTemp > 85) add(ErrorCode("A01", Severity.WARN, "控制器过温"))
            if (motorTemp > 100) add(ErrorCode("A02", Severity.WARN, "电机过温"))
        }

        return TelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh.coerceIn(0f, 150f),
            rpm = rpmI.coerceIn(0, 20000),
            gear = gear,
            odometerKm = 0f,
            tripKm = 0f,
            socPercent = ((voltageF - 48f) / (72f - 48f) * 100f).coerceIn(0f, 100f),
            packVoltage = voltageF,
            packCurrent = currentF,
            instantPowerKw = (voltageF * currentF / 1000f).coerceIn(-50f, 50f),
            estimatedRangeKm = 0f,
            motorTempC = motorTemp.toFloat(),
            controllerTempC = controllerTemp.toFloat(),
            errors = errors,
            connectionState = ConnectionState.CONNECTED
        )
    }

    private fun buildRequest(): ByteArray {
        val data = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x01, 0x01, 0x00)
        return data + checksumSumInv(data)
    }
}
