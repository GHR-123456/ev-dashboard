package com.evdash.app.ui.vehicle

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.ConnectionState
import com.evdash.app.data.TelemetrySnapshot
import com.evdash.app.ui.dashboard.DashboardViewModel
import com.evdash.app.ui.theme.Adaptive
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.EvGreen
import com.evdash.app.ui.theme.SurfaceVariantLight
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh
import com.evdash.app.ui.theme.TextMid

@Composable
fun VehicleScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("车辆信息", "系统设置")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
            .padding(Adaptive.pagePadding),
        verticalArrangement = Arrangement.spacedBy(Adaptive.pageSpacing)
    ) {
        // Header
        Text(
            text = "车辆",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = Adaptive.headerSize),
            color = TextHigh
        )

        // Tab bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val active = index == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ElectricCyan else Color.White)
                        .clickable { selectedTab = index }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (active) Color.White else TextHigh,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Tab content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            when (selectedTab) {
                0 -> VehicleInfoTab(telemetry)
                1 -> SystemSettingsTab(onNavigate)
            }
        }
    }
}

@Composable
private fun VehicleInfoTab(telemetry: TelemetrySnapshot) {
    val bleStatus = when (telemetry.connectionState) {
        ConnectionState.CONNECTED -> "已连接"
        ConnectionState.CONNECTING -> "连接中"
        ConnectionState.SCANNING -> "扫描中"
        else -> "未连接"
    }
    val batteryTemp = if (telemetry.controllerTempC != 0f) {
        "${telemetry.controllerTempC.toInt()}°C"
    } else {
        "--"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            VehicleStatRow(
                icon = Icons.Default.DirectionsCar,
                color = ElectricCyan,
                label = "车型",
                value = "EV Model S"
            )
        }
        item {
            VehicleStatRow(
                icon = Icons.Default.Speed,
                color = EvGreen,
                label = "总里程",
                value = "12,856 km"
            )
        }
        item {
            VehicleStatRow(
                icon = Icons.Default.Bluetooth,
                color = ElectricCyan,
                label = "BLE 控制器",
                value = bleStatus
            )
        }
        item {
            VehicleStatRow(
                icon = Icons.Default.Thermostat,
                color = Color(0xFFFF9800),
                label = "电池温度",
                value = batteryTemp
            )
        }
        item {
            VehicleStatRow(
                icon = Icons.Default.Monitor,
                color = TextMid,
                label = "固件版本",
                value = "v2.1.0"
            )
        }
    }
}

@Composable
private fun SystemSettingsTab(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingItem(
            icon = Icons.Default.Bluetooth,
            label = "蓝牙连接",
            value = "已启用",
            onClick = { onNavigate("devices") }
        )
        SettingItem(
            icon = Icons.Default.Settings,
            label = "单位设置",
            value = "公制",
            onClick = { onNavigate("settings") }
        )
        SettingItem(
            icon = Icons.Default.Monitor,
            label = "调试模式",
            value = "关闭",
            onClick = { onNavigate("settings") }
        )
        SettingItem(
            icon = Icons.Default.Info,
            label = "关于",
            value = "v0.2.0",
            onClick = {}
        )
    }
}

@Composable
private fun VehicleStatRow(icon: ImageVector, color: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantLight.copy(alpha = 0.4f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextDim)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantLight.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextDim, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextHigh, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextDim)
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextDim, modifier = Modifier.size(20.dp))
    }
}
