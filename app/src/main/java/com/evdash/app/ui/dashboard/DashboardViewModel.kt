package com.evdash.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evdash.app.data.ConnectionState
import com.evdash.app.data.TelemetrySnapshot
import com.evdash.app.protocol.BleManager
import com.evdash.app.protocol.DemoProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ProtocolMode { DEMO, BLE }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val demoProtocol: DemoProtocol,
    private val bleManager: BleManager
) : ViewModel() {

    /**
     * 当前数据源模式。BLE 连接成功时自动切换为 BLE，断开时回退 DEMO。
     */
    val protocolMode: StateFlow<ProtocolMode> = bleManager.connectionState
        .combine(bleManager.connectedDeviceName) { state, name ->
            when {
                state == ConnectionState.CONNECTED && name != null -> ProtocolMode.BLE
                else -> ProtocolMode.DEMO
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProtocolMode.DEMO
        )

    private val demoFlow = demoProtocol.telemetry()
        .stateIn(viewModelScope, SharingStarted.Lazily, TelemetrySnapshot.empty())

    /**
     * 遥测数据：根据模式自动切换数据源。
     * DEMO 模式用 DemoProtocol，BLE 模式用 BleManager。
     */
    val telemetry: StateFlow<TelemetrySnapshot> = combine(
        protocolMode,
        demoFlow,
        bleManager.telemetry
    ) { mode, demo, ble ->
        when (mode) {
            ProtocolMode.DEMO -> demo
            ProtocolMode.BLE -> ble
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TelemetrySnapshot.empty()
    )

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState

    val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName
}
