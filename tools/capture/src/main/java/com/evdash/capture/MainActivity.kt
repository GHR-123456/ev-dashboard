package com.evdash.capture

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var captureManager: BleCaptureManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureManager = BleCaptureManager(this)

        setContent {
            CaptureApp(captureManager) {
                requestPermissionsIfNeeded()
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.shutdown()
    }
}

@Composable
fun CaptureApp(manager: BleCaptureManager, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scanResults by manager.scanResults.collectAsState()
    val isScanning by manager.isScanning.collectAsState()
    val scanError by manager.scanError.collectAsState()
    val connectionState by manager.connectionState.collectAsState()
    val connectedDevice by manager.connectedDevice.collectAsState()
    val hexLines by manager.hexLines.collectAsState()
    val isRecording by manager.isRecording.collectAsState()
    val recordCount by manager.recordCount.collectAsState()
    val savedPath by manager.savedPath.collectAsState()

    val btEnabled = remember { isBluetoothEnabled(context) }
    val locationEnabled = remember { isLocationEnabled(context) }
    val pairedDevices = remember { manager.getPairedDevices() }
    val hasPerms = hasRequiredPermissions(context)

    LaunchedEffect(Unit) {
        onRequestPermission()
    }

    LaunchedEffect(hasPerms, btEnabled, locationEnabled) {
        if (hasPerms && btEnabled && locationEnabled && connectionState == ConnectionStatus.DISCONNECTED) {
            manager.startScan()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(hexLines.size) {
        if (hexLines.isNotEmpty()) {
            listState.animateScrollToItem(hexLines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F4F8))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EV 协议采集器",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            ConnectionBadge(connectionState, connectedDevice)
        }

        // Warnings
        if (!btEnabled) {
            WarningCard(
                icon = Icons.Default.BluetoothDisabled,
                text = "蓝牙未开启，无法扫描设备",
                action = "去开启",
                color = Color(0xFFF44336),
                onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            )
        }
        if (!locationEnabled) {
            WarningCard(
                icon = Icons.Default.LocationOn,
                text = "位置服务未开启，无法扫描周围设备",
                action = "去开启",
                color = Color(0xFFFF9800),
                onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            )
        }
        if (!hasPerms) {
            WarningCard(
                icon = Icons.Default.BluetoothDisabled,
                text = "缺少蓝牙/位置权限",
                action = "授权",
                color = Color(0xFFF44336),
                onAction = { onRequestPermission() }
            )
        }

        // Connection selector card
        BluetoothConnectionCard(
            connectionState = connectionState,
            connectedDevice = connectedDevice,
            pairedDevices = pairedDevices,
            scanResults = scanResults,
            isScanning = isScanning,
            scanError = scanError,
            btEnabled = btEnabled,
            locationEnabled = locationEnabled,
            onConnect = { manager.connect(it) },
            onDisconnect = { manager.disconnect() },
            onStartScan = { manager.startScan() },
            onStopScan = { manager.stopScan() }
        )

        // Hex log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "原始数据流 ${if (isRecording) "● 记录中 $recordCount 包" else ""}",
                        color = if (isRecording) Color(0xFF4CAF50) else Color(0xFF888888),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(
                        onClick = { manager.clearLog() },
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(hexLines) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF),
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Record / Save controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isRecording) manager.stopRecording() else manager.startRecording()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFF44336) else Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isRecording) "停止记录" else "开始记录")
            }
            Button(
                onClick = {
                    scope.launch {
                        val path = manager.saveToFile()
                        Toast.makeText(context, "已保存: $path", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = recordCount > 0,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存文件")
            }
        }

        if (savedPath != null) {
            Text(
                text = "上次保存: $savedPath",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun BluetoothConnectionCard(
    connectionState: ConnectionStatus,
    connectedDevice: ConnectedDeviceInfo?,
    pairedDevices: List<ScannedDevice>,
    scanResults: List<ScannedDevice>,
    isScanning: Boolean,
    scanError: String?,
    btEnabled: Boolean,
    locationEnabled: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Current connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (connectionState) {
                        ConnectionStatus.CONNECTED -> {
                            Icon(
                                Icons.Default.BluetoothConnected,
                                null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = connectedDevice?.name ?: "已连接",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = connectedDevice?.mac ?: "",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        ConnectionStatus.CONNECTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2196F3)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("连接中...", color = Color(0xFF2196F3), fontSize = 14.sp)
                        }
                        ConnectionStatus.DISCONNECTED -> {
                            Icon(
                                Icons.Default.BluetoothDisabled,
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("未连接设备", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }

                if (connectionState == ConnectionStatus.CONNECTED) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("断开", fontSize = 12.sp)
                    }
                }
            }

            if (btEnabled && connectionState == ConnectionStatus.DISCONNECTED) {
                Spacer(modifier = Modifier.height(10.dp))

                if (scanError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠ $scanError",
                        color = Color(0xFFF44336),
                        fontSize = 12.sp
                    )
                }

                if (!isScanning) {
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("扫描周围蓝牙设备")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            "正在扫描... 发现 ${scanResults.size} 个设备",
                            color = Color(0xFF2196F3),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onStopScan,
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                        ) {
                            Text("停止", fontSize = 12.sp)
                        }
                    }
                }

                if (scanResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "扫描到的设备 (${scanResults.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(scanResults.sortedByDescending { it.rssi }) { dev ->
                            DeviceRow(
                                device = dev,
                                isConnected = false,
                                onClick = { onConnect(dev.mac) }
                            )
                        }
                    }
                }

                if (!isScanning && scanResults.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "未发现设备，点击上方按钮开始扫描",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ScannedDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isConnected) Color(0xFFE3F2FD) else Color.Transparent)
            .clickable(enabled = !isConnected, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Bluetooth,
                null,
                tint = if (isConnected) Color(0xFF2196F3) else Color(0xFF90A4AE),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                val displayName = if (device.name == "Unknown") {
                    val shortMac = device.mac.split(":").takeLast(2).joinToString(":")
                    "Unknown ($shortMac)"
                } else {
                    device.name
                }
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isConnected) Color(0xFF1976D2) else Color.Black
                )
                Text(
                    text = device.mac,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        if (device.rssi != 0) {
            SignalBars(rssi = device.rssi)
        } else if (isConnected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    }
}

@Composable
private fun SignalBars(rssi: Int) {
    val fraction = ((rssi + 100) / 70f).coerceIn(0f, 1f)
    val color = when {
        rssi > -60 -> Color(0xFF4CAF50)
        rssi > -80 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(modifier = Modifier.width(3.dp).height(4.dp).clip(RoundedCornerShape(1.dp)).background(if (fraction > 0.2f) color else Color.LightGray))
            Box(modifier = Modifier.width(3.dp).height(7.dp).clip(RoundedCornerShape(1.dp)).background(if (fraction > 0.5f) color else Color.LightGray))
            Box(modifier = Modifier.width(3.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(if (fraction > 0.8f) color else Color.LightGray))
        }
        Text(
            text = "${rssi} dBm",
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConnectionBadge(
    connectionState: ConnectionStatus,
    connectedDevice: ConnectedDeviceInfo?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (connectionState) {
                        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionStatus.CONNECTING -> Color(0xFFFF9800)
                        ConnectionStatus.DISCONNECTED -> Color.Gray
                    }
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when (connectionState) {
                ConnectionStatus.CONNECTED -> "已连接"
                ConnectionStatus.CONNECTING -> "连接中"
                ConnectionStatus.DISCONNECTED -> "未连接"
            },
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun WarningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: String,
    color: Color,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, color = color, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.height(30.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
            ) {
                Text(action, fontSize = 12.sp, color = color)
            }
        }
    }
}

private fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled == true
}

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
}

private fun hasRequiredPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }
}
