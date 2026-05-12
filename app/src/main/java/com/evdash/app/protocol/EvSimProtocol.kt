package com.evdash.app.protocol

import com.evdash.app.data.ConnectionState
import com.evdash.app.data.Gear
import com.evdash.app.data.TelemetrySnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * 本机 PC 模拟器 (tools/ble-sim/ev_sim.py) 帧格式:
 *
 * [0]AA  [1]55  [2]ver=01  [3]seq
 * [4..5]volt*10 u16 LE  [6..7]curr*10 i16 LE  [8..9]rpm u16 LE
 * [10]speed u8  [11]tempCtrl i8  [12]tempMotor i8  [13]soc u8  [14]gear u8
 * [15]XOR(bytes[0..14])
 *
 * 10 Hz 推送, 16 字节定长, Nordic UART Service (NUS) 三 UUID 与真实控制器一致。
 */
class EvSimProtocol : ControllerProtocol {

    override val id = "ev-sim"
    override val name = "EV-SIM (测试模拟器)"
    override val description = "PC 模拟器 16 字节小端帧, 验证 BLE 链路用"

    override val serviceUuid: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    override val notifyUuid: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    override val writeUuid: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    override fun parse(bytes: ByteArray): TelemetrySnapshot? {
        if (bytes.size < 16) return null
        if (bytes[0] != 0xAA.toByte() || bytes[1] != 0x55.toByte()) return null

        var xor = 0
        for (i in 0..14) xor = xor xor (bytes[i].toInt() and 0xFF)
        if ((xor and 0xFF) != (bytes[15].toInt() and 0xFF)) return null

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val voltage = (bb.getShort(4).toInt() and 0xFFFF) / 10f
        val current = bb.getShort(6).toInt() / 10f
        val rpm = bb.getShort(8).toInt() and 0xFFFF
        val speed = bytes[10].toInt() and 0xFF
        val tempCtrl = bytes[11].toInt().toFloat()
        val tempMotor = bytes[12].toInt().toFloat()
        val soc = (bytes[13].toInt() and 0xFF).toFloat()
        val gearRaw = bytes[14].toInt() and 0xFF

        val gear = when (gearRaw) {
            2 -> Gear.D2
            3 -> Gear.D3
            else -> Gear.D1
        }

        return TelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speed.toFloat(),
            rpm = rpm,
            gear = gear,
            odometerKm = 0f,
            tripKm = 0f,
            socPercent = soc,
            packVoltage = voltage,
            packCurrent = current,
            instantPowerKw = voltage * current / 1000f,
            estimatedRangeKm = 0f,
            motorTempC = tempMotor,
            controllerTempC = tempCtrl,
            errors = emptyList(),
            connectionState = ConnectionState.CONNECTED
        )
    }
}
