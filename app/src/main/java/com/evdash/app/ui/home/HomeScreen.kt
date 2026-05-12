package com.evdash.app.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.ConnectionState
import com.evdash.app.ui.dashboard.DashboardViewModel
import com.evdash.app.ui.theme.Adaptive
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.EvGreen
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh
import com.evdash.app.ui.theme.TextMid
import com.evdash.app.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QuickItem(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormatter.format(Date())) }
    var currentDate by remember { mutableStateOf(dateFormatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = timeFormatter.format(Date())
            currentDate = dateFormatter.format(Date())
        }
    }

    val socText = if (telemetry.socPercent > 0f) "${telemetry.socPercent.toInt()}%" else "--"
    val tempText = if (telemetry.controllerTempC != 0f) "${telemetry.controllerTempC.toInt()}°C" else "--"

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
            .padding(Adaptive.pagePadding)
    ) {
        // Header: time + weather
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = Adaptive.displaySize,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextHigh
                )
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMid
                )
            }
            WeatherWidget()
        }

        Spacer(modifier = Modifier.height(Adaptive.pageSpacing))

        // Quick status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (Adaptive.isCompact) 8.dp else 12.dp)
        ) {
            StatusPill(icon = Icons.Default.BatteryFull, label = socText, color = EvGreen)
            StatusPill(icon = Icons.Default.AcUnit, label = tempText, color = ElectricCyan)
            StatusPill(icon = Icons.Default.Lock, label = "已上锁", color = TextMid)
        }

        Spacer(modifier = Modifier.height(Adaptive.pageSpacing))

        // Quick access cards
        Text(
            text = "快捷入口",
            style = MaterialTheme.typography.titleMedium,
            color = TextMid,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(if (Adaptive.isCompact) 4.dp else 8.dp))

        val quickCards = listOf(
            QuickItem("导航", Icons.Default.Navigation, ElectricCyan, "navmap"),
            QuickItem("音乐", Icons.Default.MusicNote, Color(0xFF9C27B0), "media"),
            QuickItem("车辆", Icons.Default.DirectionsCar, EvGreen, "vehicle"),
            QuickItem("状态", Icons.Default.BatteryFull, WarnAmber, "status"),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (Adaptive.isCompact) 8.dp else 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickCards) { item ->
                QuickCard(
                    label = item.label,
                    icon = item.icon,
                    color = item.color,
                    onClick = { onNavigate(item.route) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Adaptive.pageSpacing))

        // Vehicle preview card
        val isConnected = telemetry.connectionState == ConnectionState.CONNECTED
        val previewTitle = if (isConnected) "已连接控制器" else "EV Dashboard"
        val previewSubtitle = if (isConnected) "SOC ${telemetry.socPercent.toInt()}%" else "准备就绪"

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = ElectricCyan.copy(alpha = 0.3f),
                        modifier = Modifier.size(if (Adaptive.isCompact) 48.dp else 120.dp)
                    )
                    Spacer(modifier = Modifier.height(if (Adaptive.isCompact) 4.dp else 12.dp))
                    Text(
                        text = previewTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextMid
                    )
                    Text(
                        text = previewSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherWidget(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (Adaptive.isCompact) 12.dp else 16.dp, vertical = if (Adaptive.isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = null,
                tint = WarnAmber,
                modifier = Modifier.size(if (Adaptive.isCompact) 24.dp else 32.dp)
            )
            Column {
                Text(
                    text = "28°C",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHigh,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "晴",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
                Text(
                    text = "未接入天气服务",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextDim.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, label: String, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (Adaptive.isCompact) 10.dp else 14.dp, vertical = if (Adaptive.isCompact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(if (Adaptive.isCompact) 16.dp else 20.dp))
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = TextHigh, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickCard(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .width(Adaptive.quickCardWidth)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (Adaptive.isCompact) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (Adaptive.isCompact) 36.dp else 48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(if (Adaptive.isCompact) 18.dp else 24.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = TextHigh)
        }
    }
}
