package com.evdash.app.protocol

import com.evdash.app.data.ConnectionState
import com.evdash.app.data.ErrorCode
import com.evdash.app.data.Gear
import com.evdash.app.data.Severity
import com.evdash.app.data.TelemetrySnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 演示用数据源。无需任何硬件,产生平滑变化的"骑行"轨迹:速度按正弦+扰动起伏,
 * 电流/功率/温度随速度衍生,SoC 缓慢下降。10Hz 输出。
 */
@Singleton
class DemoProtocol @Inject constructor() {
    val id: String = "demo"
    val name: String = "演示数据"

    fun telemetry(): Flow<TelemetrySnapshot> = flow {
        val t0 = System.currentTimeMillis()
        var soc = 92.0f
        var motorTemp = 28.0f
        var controllerTemp = 26.0f
        var odometer = 1284.6f
        var trip = 0.0f
        var lastEmitMs = t0

        while (true) {
            val now = System.currentTimeMillis()
            val tSec = (now - t0) / 1000.0
            val dtSec = (now - lastEmitMs) / 1000.0
            lastEmitMs = now

            // 速度:基线 35,叠加 25 幅度低频正弦 + 2 幅度高频扰动
            val baseSpeed = 35f + 25f * sin(tSec / 12.0).toFloat()
            val noise = 2f * sin(tSec * 0.7).toFloat()
            val speed = (baseSpeed + noise).coerceIn(0f, 120f)
            val rpm = (speed * 75f).toInt()

            // 档位
            val gear = when {
                speed < 1f -> Gear.P
                speed < 25f -> Gear.D1
                speed < 50f -> Gear.D2
                speed < 75f -> Gear.D3
                else -> Gear.BOOST
            }

            // 电流:与速度大致正相关,叠加波动
            val current = (speed * 1.8f + 8f * sin(tSec * 0.5).toFloat()).coerceIn(-30f, 320f)
            val voltage = (72f - current * 0.012f).coerceIn(58f, 74f)
            val power = voltage * current / 1000f

            // SoC:按 dt 缓慢消耗,大致 1 小时跑完
            soc = (soc - 0.03f * dtSec.toFloat() * (power.coerceAtLeast(0f) / 4f + 0.3f))
                .coerceIn(5f, 100f)

            // 温度:与功率相关 + 自然散热
            motorTemp += ((power - 2f) * 0.06f - (motorTemp - 25f) * 0.01f) * dtSec.toFloat()
            motorTemp = motorTemp.coerceIn(25f, 120f)
            controllerTemp += ((power - 1f) * 0.04f - (controllerTemp - 25f) * 0.012f) * dtSec.toFloat()
            controllerTemp = controllerTemp.coerceIn(22f, 100f)

            // 里程累计
            val dKm = speed / 3600f * dtSec.toFloat()
            odometer += dKm
            trip += dKm

            // 剩余里程估算(假装满电 ~60km)
            val range = soc * 0.6f

            // 故障码:超温/低电触发警告
            val errors = buildList {
                if (motorTemp > 100f) add(
                    ErrorCode("E21", Severity.WARN, "电机温度偏高")
                )
                if (controllerTemp > 85f) add(
                    ErrorCode("E12", Severity.WARN, "控制器温度偏高")
                )
                if (soc < 20f) add(
                    ErrorCode("E03", Severity.WARN, "低电量,请尽快充电")
                )
            }

            emit(
                TelemetrySnapshot(
                    timestampMs = now,
                    speedKmh = speed,
                    rpm = rpm,
                    gear = gear,
                    odometerKm = odometer,
                    tripKm = trip,
                    socPercent = soc,
                    packVoltage = voltage,
                    packCurrent = current,
                    instantPowerKw = power,
                    estimatedRangeKm = range,
                    motorTempC = motorTemp,
                    controllerTempC = controllerTemp,
                    errors = errors,
                    connectionState = ConnectionState.DEMO
                )
            )
            delay(100L) // 10Hz
        }
    }
}
