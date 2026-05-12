package com.evdash.app.protocol

import com.evdash.app.data.ConnectionState
import com.evdash.app.data.ErrorCode
import com.evdash.app.data.Gear
import com.evdash.app.data.Severity
import com.evdash.app.data.TelemetrySnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * VESC (Benjamin Vedder) 电调协议。
 *
 * 使用 VESC Packet 协议：1 字节长度前缀 + payload + 2 字节 CRC16-CCITT。
 * 支持 COMM_GET_VALUES (cmd=0x04) 获取完整遥测数据。
 */
class VescProtocol : ControllerProtocol {

    override val id = "vesc"
    override val name = "VESC"

    override val serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    override val notifyUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    override val writeUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    override val requestPacket = buildRequest()
    override val pollIntervalMs = 200L

    private var buffer = byteArrayOf()

    override fun parse(bytes: ByteArray): TelemetrySnapshot? {
        buffer += bytes

        while (buffer.isNotEmpty()) {
            if (buffer.size < 3) return null
            val len = buffer[0].toInt().and(0xFF)
            if (len > 128 || len < 1) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < len + 3) return null

            val payload = buffer.copyOfRange(1, 1 + len)
            val crcRx = ((buffer[1 + len].toInt().and(0xFF) shl 8) or
                    buffer[2 + len].toInt().and(0xFF))
            val crcCalc = crc16Ccitt(payload)

            if (crcRx != crcCalc) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

            buffer = buffer.copyOfRange(len + 3, buffer.size)
            return parseGetValues(payload)
        }
        return null
    }

    private fun parseGetValues(payload: ByteArray): TelemetrySnapshot? {
        if (payload.isEmpty() || payload[0].toInt().and(0xFF) != 0x04) return null
        val data = payload.copyOfRange(1, payload.size)

        if (data.size < 56) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        val tempMos = buf.short.toFloat() * 0.1f
        val tempMotor = buf.short.toFloat() * 0.1f
        buf.position(12)
        val avgCurrent = buf.int.toFloat() * 0.001f
        buf.position(20)
        val voltage = buf.short.toFloat() * 0.1f
        buf.position(28)
        val rpm = buf.int
        buf.position(34)
        val tachometer = buf.int

        val speedKmh = kotlin.math.abs(rpm) * 0.05f

        val errors = buildList {
            if (tempMos > 85) add(ErrorCode("VSC1", Severity.WARN, "MOS 过温"))
            if (tempMotor > 100) add(ErrorCode("VSC2", Severity.WARN, "电机过温"))
            if (voltage < 40f) add(ErrorCode("VSC3", Severity.WARN, "欠压"))
        }

        return TelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh.coerceIn(0f, 200f),
            rpm = kotlin.math.abs(rpm).coerceIn(0, 50000),
            gear = Gear.D1,
            odometerKm = tachometer * 0.001f,
            tripKm = 0f,
            socPercent = ((voltage - 40f) / (60f - 40f) * 100f).coerceIn(0f, 100f),
            packVoltage = voltage,
            packCurrent = avgCurrent,
            instantPowerKw = (voltage * avgCurrent / 1000f).coerceIn(-100f, 100f),
            estimatedRangeKm = 0f,
            motorTempC = tempMotor,
            controllerTempC = tempMos,
            errors = errors,
            connectionState = ConnectionState.CONNECTED
        )
    }

    private fun buildRequest(): ByteArray {
        val payload = byteArrayOf(0x04)
        val crc = crc16Ccitt(payload)
        return byteArrayOf(payload.size.toByte()) + payload +
                byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }
}
