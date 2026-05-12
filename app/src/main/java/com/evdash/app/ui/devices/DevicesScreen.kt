package com.evdash.app.ui.devices

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.BleDevice
import com.evdash.app.data.ConnectionState
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.DangerRed
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.EvGreen
import com.evdash.app.ui.theme.SurfaceVariantLight
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh
import com.evdash.app.ui.theme.TextMid
import com.evdash.app.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DevicesScreen(
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val selectedProtocol by viewModel.selectedProtocol.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val rememberedDevice by viewModel.rememberedDevice.collectAsStateWithLifecycle()
    val autoConnectEnabled by viewModel.autoConnectEnabled.collectAsStateWithLifecycle()

    var hasPerms by remember { mutableStateOf(viewModel.hasPermissions()) }
    var isBtOn by remember { mutableStateOf(viewModel.isBluetoothEnabled()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(800)
            val newBt = viewModel.isBluetoothEnabled()
            if (newBt != isBtOn) isBtOn = newBt
        }
    }

    // 进入页面时,如果开启了自动连接且记住了设备,自动启动一次扫描
    LaunchedEffect(hasPerms, isBtOn, autoConnectEnabled, rememberedDevice?.mac) {
        if (autoConnectEnabled && rememberedDevice != null && hasPerms && isBtOn &&
            connectionState == ConnectionState.DISCONNECTED && !isScanning
        ) {
            delay(200)
            viewModel.startScan()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPerms = viewModel.hasPermissions()
    }

    val brush = remember {
        Brush.linearGradient(
            colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
            start = Offset(0f, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "蓝牙设备",
                style = MaterialTheme.typography.headlineLarge,
                color = TextHigh
            )
        }

        item {
            ConnectionStatusBar(
                state = connectionState,
                deviceName = connectedDeviceName,
                onDisconnect = viewModel::disconnect
            )
        }

        if (!hasPerms) {
            item {
                WarningCard(
                    icon = Icons.Default.LocationOn,
                    text = "需要蓝牙权限才能扫描设备",
                    action = "授权",
                    onAction = {
                        permissionLauncher.launch(viewModel.requiredPermissions().toTypedArray())
                    }
                )
            }
        } else if (!isBtOn) {
            item {
                WarningCard(
                    icon = Icons.Default.BluetoothDisabled,
                    text = "蓝牙未开启",
                    action = "去开启",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                )
            }
        }

        item {
            val protocol = selectedProtocol ?: viewModel.allProtocols.firstOrNull()
            if (protocol != null) {
                ProtocolSelector(
                    protocols = viewModel.allProtocols,
                    selected = protocol,
                    onSelect = { viewModel.selectProtocol(it.id) }
                )
            }
        }

        item {
            AutoConnectCard(
                enabled = autoConnectEnabled,
                remembered = rememberedDevice,
                onToggle = viewModel::setAutoConnectEnabled,
                onForget = viewModel::forgetRememberedDevice
            )
        }

        item {
            scanError?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "⚠ $msg",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.startScan() },
                    enabled = hasPerms && isBtOn && !isScanning && connectionState != ConnectionState.CONNECTING,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isScanning) "扫描中..." else "开始扫描")
                }
                OutlinedButton(
                    onClick = { viewModel.stopScan() },
                    enabled = isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止扫描", color = TextMid)
                }
            }
        }

        if (scanResults.isEmpty() && !isScanning) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "点击「开始扫描」搜索附近的 BLE 设备",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextDim
                    )
                }
            }
        }

        items(scanResults, key = { it.mac }) { device ->
            DeviceItem(
                device = device,
                isConnected = connectionState == ConnectionState.CONNECTED,
                connectionState = connectionState,
                onConnect = { viewModel.connect(device.mac) }
            )
        }
    }
}

@Composable
private fun ConnectionStatusBar(
    state: ConnectionState,
    deviceName: String?,
    onDisconnect: () -> Unit
) {
    val (icon, color, text) = when (state) {
        ConnectionState.DISCONNECTED -> Triple(Icons.Default.BluetoothDisabled, TextDim, "未连接")
        ConnectionState.SCANNING -> Triple(Icons.AutoMirrored.Filled.BluetoothSearching, WarnAmber, "扫描中...")
        ConnectionState.CONNECTING -> Triple(Icons.AutoMirrored.Filled.BluetoothSearching, WarnAmber, "连接中...")
        ConnectionState.CONNECTED -> Triple(Icons.Default.BluetoothConnected, EvGreen, "已连接: ${deviceName ?: "未知设备"}")
        ConnectionState.DEMO -> Triple(Icons.Default.Bluetooth, ElectricCyan, "演示模式")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
            if (state == ConnectionState.CONNECTED) {
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Default.LinkOff, contentDescription = "断开", tint = DangerRed)
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: BleDevice,
    isConnected: Boolean,
    connectionState: ConnectionState,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) SurfaceVariantLight.copy(alpha = 0.5f) else Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isConnected) 6.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // RSSI indicator
            RssiIndicator(rssi = device.rssi)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "未知设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHigh
                )
                Text(
                    text = device.mac,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim
                )
            }

            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = rssiColor(device.rssi)
            )

            Button(
                onClick = onConnect,
                enabled = !isConnected && connectionState != ConnectionState.CONNECTING,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("连接")
            }
        }
    }
}

@Composable
private fun RssiIndicator(rssi: Int) {
    val fraction = ((rssi + 100) / 70f).coerceIn(0f, 1f)
    val color = rssiColor(rssi)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        modifier = Modifier.height(28.dp)
    ) {
        Box(modifier = Modifier.width(4.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(if (fraction > 0.2f) color else TextDim))
        Box(modifier = Modifier.width(4.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(if (fraction > 0.5f) color else TextDim))
        Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(if (fraction > 0.8f) color else TextDim))
    }
}

private fun rssiColor(rssi: Int): Color = when {
    rssi > -60 -> EvGreen
    rssi > -80 -> WarnAmber
    else -> DangerRed
}

@Composable
private fun ProtocolSelector(
    protocols: List<com.evdash.app.protocol.ControllerProtocol>,
    selected: com.evdash.app.protocol.ControllerProtocol,
    onSelect: (com.evdash.app.protocol.ControllerProtocol) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("控制器品牌", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                protocols.forEach { protocol ->
                    val active = protocol.id == selected.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) ElectricCyan else SurfaceVariantLight)
                            .clickable { onSelect(protocol) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = protocol.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) Color.Black else TextMid
                            )
                            if (protocol.description.isNotBlank()) {
                                Text(
                                    text = protocol.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Color.Black.copy(alpha = 0.7f) else TextDim,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarnAmber.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = WarnAmber, modifier = Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = WarnAmber, modifier = Modifier.weight(1f))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = WarnAmber, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(action)
            }
        }
    }
}

@Composable
private fun AutoConnectCard(
    enabled: Boolean,
    remembered: com.evdash.app.protocol.RememberedDevice?,
    onToggle: (Boolean) -> Unit,
    onForget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (enabled) ElectricCyan else TextDim,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动连接",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHigh
                    )
                    Text(
                        text = if (remembered == null) "尚未连接过任何设备" else "扫到已记住的设备时自动连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = remembered != null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricCyan
                    )
                )
            }
            if (remembered != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = remembered.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHigh,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${remembered.mac} · ${remembered.protocolId.ifBlank { "?" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim
                        )
                    }
                    OutlinedButton(
                        onClick = onForget,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("忘记", color = DangerRed)
                    }
                }
            }
        }
    }
}
