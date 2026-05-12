package com.evdash.app.ui.devices

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.evdash.app.data.BleDevice
import com.evdash.app.data.ConnectionState
import com.evdash.app.protocol.BleManager
import com.evdash.app.protocol.ControllerProtocol
import com.evdash.app.protocol.ProtocolRegistry
import com.evdash.app.protocol.RememberedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DevicesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: BleManager
) : ViewModel() {

    val scanResults: StateFlow<List<BleDevice>> = bleManager.scanResults
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName
    val selectedProtocol: StateFlow<ControllerProtocol?> = bleManager.selectedProtocol
    val scanError: StateFlow<String?> = bleManager.scanError
    val rememberedDevice: StateFlow<RememberedDevice?> = bleManager.rememberedDevice
    val autoConnectEnabled: StateFlow<Boolean> = bleManager.autoConnectEnabled

    val allProtocols: List<ControllerProtocol> = ProtocolRegistry.all

    fun selectProtocol(id: String) {
        bleManager.selectProtocol(id)
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(mac: String) {
        bleManager.connect(mac)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        bleManager.setAutoConnectEnabled(enabled)
    }

    fun forgetRememberedDevice() {
        bleManager.forgetRememberedDevice()
    }

    fun isBluetoothEnabled(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
