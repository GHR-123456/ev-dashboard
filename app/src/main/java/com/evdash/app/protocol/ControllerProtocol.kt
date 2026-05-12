package com.evdash.app.protocol

import com.evdash.app.data.TelemetrySnapshot
import java.util.UUID

/**
 * 电动车控制器蓝牙协议。
 *
 * 每种控制器一个独立实现，直接硬编码解析规则。
 * 没有通用框架，没有复杂抽象——拿到数据就按破解后的格式暴力解析。
 */
interface ControllerProtocol {
    /** 协议唯一标识，如 "apt", "votol", "yuanqu_360" */
    val id: String

    /** 显示名称 */
    val name: String

    /** 协议描述（可选） */
    val description: String get() = ""

    /** BLE 服务 UUID */
    val serviceUuid: UUID

    /** 通知特征 UUID */
    val notifyUuid: UUID

    /** 写入特征 UUID */
    val writeUuid: UUID

    /**
     * 解析原始字节流。
     * 自己处理粘包、校验、字段提取。越简单越好。
     * @return 解析成功返回 TelemetrySnapshot，失败返回 null
     */
    fun parse(bytes: ByteArray): TelemetrySnapshot?

    /** 主动请求数据包，不需要轮询返回 null */
    val requestPacket: ByteArray? get() = null

    /** 轮询间隔（毫秒），不需要轮询返回 0 */
    val pollIntervalMs: Long get() = 0
}
