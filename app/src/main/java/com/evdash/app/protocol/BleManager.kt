package com.evdash.app.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.evdash.app.data.BleDevice
import com.evdash.app.data.ConnectionState
import com.evdash.app.data.PacketDirection
import com.evdash.app.data.RawPacket
import com.evdash.app.data.TelemetrySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BLE 管理器：扫描、连接、GATT 通信、字节流嗅探、协议切换。
 */
@Singleton
@SuppressLint("MissingPermission")
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rememberedDeviceStore: RememberedDeviceStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var scanner = bluetoothAdapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    // ---- Protocol ----

    private var _currentProtocol: ControllerProtocol? = null
    private val _selectedProtocol = MutableStateFlow<ControllerProtocol?>(null)
    val selectedProtocol: StateFlow<ControllerProtocol?> = _selectedProtocol.asStateFlow()

    fun selectProtocol(protocol: ControllerProtocol) {
        _currentProtocol = protocol
        _selectedProtocol.value = protocol
    }

    fun selectProtocol(id: String) {
        val p = ProtocolRegistry.find(id)
        _currentProtocol = p
        _selectedProtocol.value = p
    }

    val currentProtocol: ControllerProtocol? get() = _currentProtocol

    init {
        // 默认选中第一个协议(EvSimProtocol),避免用户未点选时 _currentProtocol 为 null 导致丢帧
        ProtocolRegistry.all.firstOrNull()?.let {
            _currentProtocol = it
            _selectedProtocol.value = it
        }
    }

    // ---- Public StateFlows ----

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetrySnapshot.empty())
    val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    private val _packets = MutableStateFlow<List<RawPacket>>(emptyList())
    val packets: StateFlow<List<RawPacket>> = _packets.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private var lastConnectMac: String? = null
    private var connectRetryCount = 0
    private val maxConnectRetry = 2
    private val notifyQueue = mutableListOf<BluetoothGattCharacteristic>()
    private var scanJob: kotlinx.coroutines.Job? = null

    // 扫描列表的内部聚合状态:EMA 平滑 RSSI + 600ms 节流发布,避免抖动导致列表频繁换位
    private val deviceMap = mutableMapOf<String, BleDevice>()
    private var publishJob: kotlinx.coroutines.Job? = null
    private val rssiSmoothAlpha = 0.3f
    private val publishThrottleMs = 600L

    // 自动连接:用户主动 disconnect 后,在下次手动 startScan/connect 之前都不自动连
    private var suppressAutoConnect = false

    // ---- Remembered device (auto-connect) ----

    val rememberedDevice: StateFlow<RememberedDevice?> = rememberedDeviceStore.rememberedDevice
    val autoConnectEnabled: StateFlow<Boolean> = rememberedDeviceStore.autoConnectEnabled

    fun setAutoConnectEnabled(enabled: Boolean) {
        rememberedDeviceStore.setAutoConnectEnabled(enabled)
    }

    fun forgetRememberedDevice() {
        rememberedDeviceStore.forget()
    }

    // ---- Internal ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val mac = device.address
            val newRssi = result.rssi
            val prev = deviceMap[mac]
            val smoothedRssi = if (prev == null) {
                newRssi
            } else {
                (prev.rssi * (1f - rssiSmoothAlpha) + newRssi * rssiSmoothAlpha).toInt()
            }
            val name = device.name ?: prev?.name ?: "Unknown"
            deviceMap[mac] = BleDevice(
                mac = mac,
                name = name,
                rssi = smoothedRssi,
                lastSeenMs = System.currentTimeMillis()
            )
            schedulePublish()
            maybeAutoConnect(mac)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _scanError.value = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败,请检查权限"
                SCAN_FAILED_INTERNAL_ERROR -> "蓝牙内部错误"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持 BLE 扫描"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙硬件资源不足"
                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "扫描过于频繁,请稍后再试"
                else -> "扫描失败 (错误码: $errorCode)"
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectRetryCount = 0
                        this@BleManager.gatt = gatt
                        val deviceName = gatt.device.name
                        _connectedDeviceName.value = deviceName
                        // 记住这台设备 + 当前选中的协议,供下次自动连接
                        val mac = gatt.device.address
                        val name = deviceName ?: "未命名"
                        val protocolId = _currentProtocol?.id ?: ""
                        rememberedDeviceStore.remember(mac, name, protocolId)
                        gatt.discoverServices()
                    } else {
                        try { gatt.close() } catch (_: Exception) {}
                        this@BleManager.gatt = null
                        attemptRetryOrFail(status)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    try { gatt.close() } catch (_: Exception) {}
                    this@BleManager.gatt = null
                    stopPolling()
                    attemptRetryOrFail(status)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                return
            }
            notifyQueue.clear()
            val protocol = _currentProtocol
            val targetChar = protocol?.let {
                gatt.getService(it.serviceUuid)?.getCharacteristic(it.notifyUuid)
            }
            if (targetChar != null) {
                notifyQueue.add(targetChar)
            } else {
                gatt.services.flatMap { it.characteristics }
                    .filter {
                        (it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                                BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                    }
                    .forEach { notifyQueue.add(it) }
            }
            _connectionState.value = ConnectionState.CONNECTED
            enableNextNotification(gatt)
            startPolling()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            enableNextNotification(gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // API < 33 派发的旧签名;新 API 上 characteristic.value 不可靠,因此优先用新签名
            val bytes = characteristic.value ?: return
            handleNotification(bytes)
        }

        // API 33+ (Android 13+) 派发的新签名,value 直接以参数传入,可靠
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val bytes = characteristic.value ?: return
                logPacket(bytes, PacketDirection.RX)
            }
        }
    }

    private fun handleNotification(bytes: ByteArray) {
        logPacket(bytes, PacketDirection.RX)
        val protocol = _currentProtocol ?: return
        val parsed = protocol.parse(bytes) ?: return
        _telemetry.value = parsed
    }

    private fun attemptRetryOrFail(status: Int) {
        val retryable = status == 8 || status == 19 || status == 22 ||
                status == 62 || status == 133 || status == 147
        val mac = lastConnectMac
        if (retryable && mac != null && connectRetryCount < maxConnectRetry) {
            connectRetryCount++
            scope.launch {
                delay(1500)
                doConnect(mac)
            }
        } else {
            connectRetryCount = 0
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
        }
    }

    // ---- Notifications ----

    private fun enableNextNotification(gatt: BluetoothGatt) {
        val char = notifyQueue.removeFirstOrNull() ?: return
        val ok = gatt.setCharacteristicNotification(char, true)
        if (!ok) {
            enableNextNotification(gatt)
            return
        }
        val cccd = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd == null) {
            enableNextNotification(gatt)
            return
        }
        cccd.value = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        gatt.writeDescriptor(cccd)
    }

    // ---- Polling ----

    private var pollingJob: kotlinx.coroutines.Job? = null

    private fun startPolling() {
        stopPolling()
        val protocol = _currentProtocol ?: return
        val interval = protocol.pollIntervalMs
        val packet = protocol.requestPacket ?: return
        if (interval <= 0) return

        pollingJob = scope.launch {
            while (true) {
                sendRaw(packet)
                delay(interval)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ---- Scan ----

    private fun schedulePublish() {
        if (publishJob?.isActive == true) return
        publishJob = scope.launch {
            _scanResults.value = deviceMap.values
                .sortedByDescending { it.rssi }
                .toList()
            delay(publishThrottleMs)
        }
    }

    private fun maybeAutoConnect(scannedMac: String) {
        if (suppressAutoConnect) return
        if (!autoConnectEnabled.value) return
        val remembered = rememberedDevice.value ?: return
        if (remembered.mac != scannedMac) return
        val state = _connectionState.value
        if (state != ConnectionState.DISCONNECTED) return
        if (remembered.protocolId.isNotBlank() && _currentProtocol?.id != remembered.protocolId) {
            selectProtocol(remembered.protocolId)
        }
        connect(scannedMac)
    }

    fun startScan() {
        if (_isScanning.value) return
        val s = scanner ?: run {
            _scanError.value = "BLE 扫描器不可用"
            return
        }
        // 用户主动扫描 → 允许自动连接重新生效
        suppressAutoConnect = false
        _isScanning.value = true
        _scanError.value = null
        _scanResults.value = emptyList()
        deviceMap.clear()
        publishJob?.cancel()
        publishJob = null
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()
        try {
            s.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            _isScanning.value = false
            _scanError.value = "启动扫描失败: ${e.message}"
            return
        }

        scanJob?.cancel()
        scanJob = scope.launch {
            delay(30000)
            stopScan()
        }
    }

    fun stopScan() {
        _isScanning.value = false
        scanJob?.cancel()
        scanJob = null
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    // ---- Connect / Disconnect ----

    fun connect(mac: String) {
        if (bluetoothAdapter == null) return
        suppressAutoConnect = false
        lastConnectMac = mac
        connectRetryCount = 0
        doConnect(mac)
    }

    private fun doConnect(mac: String) {
        val adapter = bluetoothAdapter ?: return

        // 扫描严重干扰连接,必须先停
        stopScan()

        // 清理旧 GATT,避免句柄泄露
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        notifyQueue.clear()

        _connectionState.value = ConnectionState.CONNECTING

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        // 延迟一点让蓝牙栈清理扫描状态
        scope.launch {
            delay(300)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
            } catch (_: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        // 用户主动断开 → 抑制自动连接,直到用户再次扫描/连接
        suppressAutoConnect = true
        lastConnectMac = null
        connectRetryCount = 0
        stopPolling()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    // ---- TX ----

    fun sendRaw(bytes: ByteArray): Boolean {
        logPacket(bytes, PacketDirection.TX)
        val g = gatt ?: return false
        val protocol = _currentProtocol ?: return false
        val service = g.getService(protocol.serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(protocol.writeUuid) ?: return false
        characteristic.value = bytes
        return g.writeCharacteristic(characteristic)
    }

    // ---- Sniffer ----

    fun clearPackets() {
        _packets.value = emptyList()
    }

    private fun logPacket(bytes: ByteArray, direction: PacketDirection) {
        val packet = RawPacket(
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            data = bytes.copyOf()
        )
        val current = _packets.value
        _packets.value = (current + packet).takeLast(500)
    }

    // ---- Cleanup ----

    fun shutdown() {
        stopScan()
        disconnect()
        scope.cancel()
    }
}
