package com.evdash.capture

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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class BleCaptureManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null

    private val nusServiceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val notifyUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    // ---- State ----

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scanResults: StateFlow<List<ScannedDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDeviceInfo?>(null)
    val connectedDevice: StateFlow<ConnectedDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _hexLines = MutableStateFlow<List<String>>(emptyList())
    val hexLines: StateFlow<List<String>> = _hexLines.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordCount = MutableStateFlow(0)
    val recordCount: StateFlow<Int> = _recordCount.asStateFlow()

    private val _savedPath = MutableStateFlow<String?>(null)
    val savedPath: StateFlow<String?> = _savedPath.asStateFlow()

    private val rawBuffer = mutableListOf<ByteArray>()
    private var recordStartTime: Long = 0
    private var scanJob: kotlinx.coroutines.Job? = null
    private var receiverRegistered = false
    private var lastConnectMac: String? = null
    private var connectRetryCount = 0
    private val maxConnectRetry = 2

    // ---- Classic Bluetooth Discovery ----

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    if (device == null) return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val existing = _scanResults.value
                    val newDev = ScannedDevice(
                        mac = device.address,
                        name = device.name ?: "Unknown",
                        rssi = if (rssi == Short.MIN_VALUE.toInt()) -100 else rssi
                    )
                    val updated = if (existing.any { it.mac == device.address }) {
                        existing.map { if (it.mac == device.address) newDev else it }
                    } else {
                        existing + newDev
                    }
                    _scanResults.value = updated.sortedByDescending { it.rssi }
                }
            }
        }
    }

    private fun registerDiscoveryReceiver() {
        if (receiverRegistered) return
        try {
            context.registerReceiver(discoveryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            receiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun unregisterDiscoveryReceiver() {
        if (!receiverRegistered) return
        try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
        receiverRegistered = false
    }

    // ---- Paired Devices ----

    fun getPairedDevices(): List<ScannedDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return adapter.bondedDevices?.map {
            ScannedDevice(
                mac = it.address,
                name = it.name ?: "Unknown",
                rssi = 0
            )
        } ?: emptyList()
    }

    // ---- Scan ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val existing = _scanResults.value
            val updated = existing.filter { it.mac != device.address } +
                    ScannedDevice(
                        mac = device.address,
                        name = device.name ?: "Unknown",
                        rssi = result.rssi
                    )
            _scanResults.value = updated.sortedByDescending { it.rssi }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            val existing = _scanResults.value.toMutableList()
            val macSet = existing.map { it.mac }.toMutableSet()
            for (result in results) {
                val dev = result.device
                if (dev.address !in macSet) {
                    existing.add(ScannedDevice(dev.address, dev.name ?: "Unknown", result.rssi))
                    macSet.add(dev.address)
                }
            }
            _scanResults.value = existing.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            val msg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败，请检查权限"
                SCAN_FAILED_INTERNAL_ERROR -> "蓝牙内部错误"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持 BLE 扫描"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙硬件资源不足"
                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "扫描过于频繁，请稍后再试"
                else -> "扫描失败 (错误码: $errorCode)"
            }
            _scanError.value = msg
        }
    }

    fun startScan() {
        if (_isScanning.value) return
        val adapter = bluetoothAdapter ?: run {
            _scanError.value = "蓝牙适配器不可用"
            return
        }
        if (!adapter.isEnabled) {
            _scanError.value = "蓝牙未开启"
            return
        }
        val s = adapter.bluetoothLeScanner ?: run {
            _scanError.value = "BLE 扫描器不可用"
            return
        }

        _isScanning.value = true
        _scanError.value = null
        _scanResults.value = emptyList()

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

        // 同时启动经典蓝牙发现(BR/EDR),用于扫描非 BLE 设备
        registerDiscoveryReceiver()
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: Exception) {}

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
        val adapter = bluetoothAdapter ?: return
        val s = adapter.bluetoothLeScanner
        try { s?.stopScan(scanCallback) } catch (_: Exception) {}
        try { if (adapter.isDiscovering) adapter.cancelDiscovery() } catch (_: Exception) {}
        unregisterDiscoveryReceiver()
    }

    // ---- Connect ----

    fun connect(mac: String) {
        if (bluetoothAdapter == null) return
        lastConnectMac = mac
        connectRetryCount = 0

        // 信号强度提示
        val deviceInList = _scanResults.value.find { it.mac == mac }
        val rssi = deviceInList?.rssi ?: 0
        if (rssi != 0 && rssi < -80) {
            appendInfoLine("⚠ 信号弱 ($rssi dBm),建议手机靠近设备 1 米内")
        }

        doConnect(mac)
    }

    private fun doConnect(mac: String) {
        val adapter = bluetoothAdapter ?: return

        // 关键: 连接前必须停止扫描,扫描会严重干扰连接
        stopScan()

        // 清理旧 GATT,避免句柄泄露
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        notifyQueue.clear()

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: Exception) {
            appendInfoLine("✗ 无效的 MAC: $mac (${e.message})")
            _connectionState.value = ConnectionStatus.DISCONNECTED
            return
        }

        _connectionState.value = ConnectionStatus.CONNECTING
        _connectedDevice.value = ConnectedDeviceInfo(
            name = device.name ?: mac,
            mac = device.address
        )
        val retryTag = if (connectRetryCount > 0) " [重试 $connectRetryCount/$maxConnectRetry]" else ""
        appendInfoLine("→ 正在连接 ${device.name ?: mac} ($mac)$retryTag...")

        // 延迟一点让蓝牙栈清理扫描状态
        scope.launch {
            delay(300)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
            } catch (e: Exception) {
                appendInfoLine("✗ connectGatt 异常: ${e.message}")
                _connectionState.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        lastConnectMac = null  // 防止用户主动断开后触发自动重试
        connectRetryCount = 0
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
        _connectedDevice.value = null
    }

    private val notifyQueue = mutableListOf<BluetoothGattCharacteristic>()

    private fun enableNextNotification(gatt: BluetoothGatt) {
        if (notifyQueue.isEmpty()) {
            appendInfoLine("✓ 所有通知已订阅,等待数据...")
            return
        }
        val char = notifyQueue.removeAt(0)
        val ok = gatt.setCharacteristicNotification(char, true)
        if (!ok) {
            appendInfoLine("✗ setCharacteristicNotification 失败: ${char.uuid}")
            enableNextNotification(gatt)
            return
        }
        val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor == null) {
            appendInfoLine("✓ 已启用通知 (无 CCCD): ${char.uuid}")
            enableNextNotification(gatt)
            return
        }
        val value = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        @Suppress("DEPRECATION")
        descriptor.value = value
        @Suppress("DEPRECATION")
        gatt.writeDescriptor(descriptor)
    }

    private fun appendInfoLine(text: String) {
        val timestamp = formatTime(System.currentTimeMillis())
        val line = "[INFO $timestamp] $text"
        _hexLines.value = (_hexLines.value + line).takeLast(500)
    }

    private fun gattStatusDesc(status: Int): String = when (status) {
        0 -> "成功"
        8 -> "连接超时,设备无响应"
        19 -> "设备主动断开"
        22 -> "本地主动断开"
        34 -> "LMP 响应超时"
        62 -> "建立连接失败"
        133 -> "通用 GATT 错误,通常需要重试"
        147 -> "GATT 错误 (建议距离更近后重试)"
        256 -> "连接被取消"
        else -> "未知"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectRetryCount = 0
                        this@BleCaptureManager.gatt = gatt
                        appendInfoLine("✓ 已连接,正在发现服务...")
                        gatt.discoverServices()
                    } else {
                        appendInfoLine("✗ 连接异常 status=$status (${gattStatusDesc(status)})")
                        try { gatt.close() } catch (_: Exception) {}
                        _connectionState.value = ConnectionStatus.DISCONNECTED
                        this@BleCaptureManager.gatt = null
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    appendInfoLine("连接断开 status=$status (${gattStatusDesc(status)})")
                    try { gatt.close() } catch (_: Exception) {}
                    this@BleCaptureManager.gatt = null

                    // 自动重试: 仅当 status 表示可重试错误,且未达上限,且非用户主动断开
                    val retryable = status == 8 || status == 19 || status == 22 ||
                            status == 62 || status == 133 || status == 147
                    val mac = lastConnectMac
                    if (retryable && mac != null && connectRetryCount < maxConnectRetry) {
                        connectRetryCount++
                        appendInfoLine("→ 1.5 秒后自动重试 ($connectRetryCount/$maxConnectRetry)...")
                        scope.launch {
                            delay(1500)
                            doConnect(mac)
                        }
                    } else {
                        if (retryable && connectRetryCount >= maxConnectRetry) {
                            appendInfoLine("✗ 已重试 $maxConnectRetry 次仍失败,建议: 重启蓝牙 / 关闭其他蓝牙 App / 靠近设备")
                        }
                        connectRetryCount = 0
                        _connectionState.value = ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendInfoLine("服务发现失败 (status=$status)")
                _connectionState.value = ConnectionStatus.DISCONNECTED
                return
            }

            notifyQueue.clear()
            val services = gatt.services
            appendInfoLine("发现 ${services.size} 个 GATT 服务:")

            for (service in services) {
                appendInfoLine("┌ Service ${service.uuid}")
                for (char in service.characteristics) {
                    val props = mutableListOf<String>()
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("R")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("W")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("WnR")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("N")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("I")
                    appendInfoLine("│  ↳ ${char.uuid} [${props.joinToString(",")}]")

                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    ) {
                        notifyQueue.add(char)
                    }
                }
            }

            _connectionState.value = ConnectionStatus.CONNECTED

            if (notifyQueue.isEmpty()) {
                appendInfoLine("⚠ 未发现 notify/indicate 特征,可能需要主动读取或写入触发数据")
            } else {
                appendInfoLine("订阅 ${notifyQueue.size} 个通知特征...")
                enableNextNotification(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            enableNextNotification(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val bytes = characteristic.value ?: return
            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            val timestamp = System.currentTimeMillis()
            val shortUuid = characteristic.uuid.toString().substring(4, 8)
            val line = "[RX ${formatTime(timestamp)} $shortUuid] $hex"

            val current = _hexLines.value
            _hexLines.value = (current + line).takeLast(500)

            if (_isRecording.value) {
                rawBuffer.add(bytes)
                _recordCount.value = rawBuffer.size
            }
        }
    }

    // ---- Recording ----

    fun startRecording() {
        rawBuffer.clear()
        _recordCount.value = 0
        _savedPath.value = null
        _isRecording.value = true
        recordStartTime = System.currentTimeMillis()
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    suspend fun saveToFile(): String = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val subDir = File(dir, "ev-capture")
        subDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(recordStartTime))
        val file = File(subDir, "capture_$timestamp.bin")

        file.outputStream().use { out ->
            rawBuffer.forEach { out.write(it) }
        }

        val txtFile = File(subDir, "capture_$timestamp.txt")
        txtFile.bufferedWriter().use { writer ->
            writer.write("# EV Capture Log\n")
            writer.write("# Time: $timestamp\n")
            writer.write("# Packets: ${rawBuffer.size}\n")
            writer.write("# Total bytes: ${rawBuffer.sumOf { it.size }}\n")
            writer.write("# ---\n")
            _hexLines.value.forEach { writer.write("$it\n") }
        }

        _savedPath.value = txtFile.absolutePath
        txtFile.absolutePath
    }

    fun clearLog() {
        _hexLines.value = emptyList()
        rawBuffer.clear()
        _recordCount.value = 0
        _savedPath.value = null
    }

    fun shutdown() {
        stopScan()
        disconnect()
        scope.cancel()
    }

    private fun formatTime(ms: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(ms))
    }
}

data class ScannedDevice(
    val mac: String,
    val name: String,
    val rssi: Int
)

data class ConnectedDeviceInfo(
    val name: String,
    val mac: String
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
