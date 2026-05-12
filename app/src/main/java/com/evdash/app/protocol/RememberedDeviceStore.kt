package com.evdash.app.protocol

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RememberedDevice(
    val mac: String,
    val name: String,
    val protocolId: String
)

/**
 * 持久化"最近一次成功连接的设备",支持全局自动连接开关。
 *
 * 触发时机:BleManager 在 onConnectionStateChange 收到 STATE_CONNECTED + GATT_SUCCESS 时记录;
 * 扫描过程中 onScanResult 命中记忆的 MAC 且开关开启时自动 connect。
 */
@Singleton
class RememberedDeviceStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ble_remembered", Context.MODE_PRIVATE)

    private val _rememberedDevice = MutableStateFlow(loadDevice())
    val rememberedDevice: StateFlow<RememberedDevice?> = _rememberedDevice.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    fun remember(mac: String, name: String, protocolId: String) {
        prefs.edit()
            .putString(KEY_MAC, mac)
            .putString(KEY_NAME, name)
            .putString(KEY_PROTOCOL, protocolId)
            .apply()
        _rememberedDevice.value = RememberedDevice(mac, name, protocolId)
    }

    fun forget() {
        prefs.edit()
            .remove(KEY_MAC)
            .remove(KEY_NAME)
            .remove(KEY_PROTOCOL)
            .apply()
        _rememberedDevice.value = null
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _autoConnectEnabled.value = enabled
    }

    private fun loadDevice(): RememberedDevice? {
        val mac = prefs.getString(KEY_MAC, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: "未命名"
        val protocolId = prefs.getString(KEY_PROTOCOL, null) ?: ""
        return RememberedDevice(mac, name, protocolId)
    }

    private companion object {
        const val KEY_MAC = "remembered_mac"
        const val KEY_NAME = "remembered_name"
        const val KEY_PROTOCOL = "remembered_protocol_id"
        const val KEY_AUTO = "auto_connect_enabled"
    }
}
