package com.evdash.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.map.UpdateState
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.EvGreen
import com.evdash.app.ui.theme.SurfaceVariantLight
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh
import com.evdash.app.ui.theme.TextMid
import com.evdash.app.ui.theme.WarnAmber

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
    val useCelsius by viewModel.useCelsius.collectAsStateWithLifecycle()
    val use24h by viewModel.use24h.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val forceDemo by viewModel.forceDemo.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()

    val brush = remember {
        Brush.linearGradient(
            colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
            start = Offset(0f, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineLarge,
            color = TextHigh
        )

        SettingsGroup(title = "单位") {
            SettingChoiceRow(
                icon = Icons.Default.Speed,
                iconColor = ElectricCyan,
                title = "速度单位",
                options = listOf("km/h" to true, "mph" to false),
                selected = useMetric,
                onSelect = viewModel::setUseMetric
            )
            SettingChoiceRow(
                icon = Icons.Default.Thermostat,
                iconColor = EvGreen,
                title = "温度单位",
                options = listOf("°C" to true, "°F" to false),
                selected = useCelsius,
                onSelect = viewModel::setUseCelsius
            )
            SettingChoiceRow(
                icon = Icons.Default.Schedule,
                iconColor = WarnAmber,
                title = "时间格式",
                options = listOf("24h" to true, "12h" to false),
                selected = use24h,
                onSelect = viewModel::setUse24h
            )
        }

        SettingsGroup(title = "显示") {
            SettingToggleRow(
                icon = Icons.Default.Brightness6,
                iconColor = WarnAmber,
                title = "屏幕常亮",
                subtitle = "驾驶时不让屏幕熄灭",
                checked = keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn
            )
        }

        SettingsGroup(title = "数据源 & 开发") {
            SettingToggleRow(
                icon = Icons.Default.Science,
                iconColor = ElectricCyan,
                title = "强制演示数据",
                subtitle = "即使蓝牙已连接也使用模拟数据",
                checked = forceDemo,
                onCheckedChange = viewModel::setForceDemo
            )
            SettingToggleRow(
                icon = Icons.Default.BugReport,
                iconColor = EvGreen,
                title = "调试模式",
                subtitle = "在嗅探页显示原始 BLE 帧",
                checked = debugMode,
                onCheckedChange = viewModel::setDebugMode
            )
        }

        SettingsGroup(title = "地图数据") {
            MapUpdateRow(
                version = mapState.packageMeta?.version ?: "未安装",
                updateState = mapState.updateState,
                lastCheckedAt = mapState.lastCheckedAt,
                lastError = mapState.lastError,
                onCheckNow = viewModel::checkForMapUpdateNow
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("EV Dashboard", style = MaterialTheme.typography.titleMedium, color = TextHigh)
                Text("版本 0.1.0-mvp2", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                Text(
                    "M2 仪表 / M3 BLE / M4 嗅探 / M5 前台服务",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextMid,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.width(20.dp).height(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = iconColor,
                checkedTrackColor = iconColor.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingChoiceRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    options: List<Pair<String, Boolean>>,
    selected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.width(20.dp).height(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) ->
                val active = selected == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) iconColor else SurfaceVariantLight)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) Color.Black else TextMid
                    )
                }
            }
        }
    }
}

@Composable
private fun MapUpdateRow(
    version: String,
    updateState: UpdateState,
    lastCheckedAt: Long,
    lastError: String?,
    onCheckNow: () -> Unit
) {
    val statusText = when (updateState) {
        UpdateState.Idle -> if (lastCheckedAt > 0L) "最近检查:${formatRelative(lastCheckedAt)}" else "尚未检查"
        UpdateState.Pending -> "等待网络…"
        is UpdateState.Running -> "下载中 ${updateState.percent}%(${updateState.stage})"
        is UpdateState.Success -> "已更新到 ${updateState.version}"
        is UpdateState.Failed -> "失败:${updateState.reason}"
    }
    val busy = updateState is UpdateState.Pending || updateState is UpdateState.Running

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onCheckNow() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ElectricCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.width(20.dp).height(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("离线地图包", style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            Text("版本 $version · $statusText", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            if (lastError != null && updateState !is UpdateState.Running) {
                Text("上次错误:$lastError", style = MaterialTheme.typography.labelMedium, color = WarnAmber)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (busy) SurfaceVariantLight else ElectricCyan)
                .clickable(enabled = !busy) { onCheckNow() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = if (busy) TextMid else Color.Black,
                    modifier = Modifier.width(16.dp).height(16.dp)
                )
                Text(
                    text = if (busy) "更新中" else "立即检查",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (busy) TextMid else Color.Black
                )
            }
        }
    }
}

private fun formatRelative(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val mins = diff / 60_000
    return when {
        mins < 1 -> "刚刚"
        mins < 60 -> "${mins}分钟前"
        mins < 60 * 24 -> "${mins / 60}小时前"
        else -> "${mins / (60 * 24)}天前"
    }
}
