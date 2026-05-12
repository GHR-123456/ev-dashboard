package com.evdash.app.ui.status

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.ConnectionState
import com.evdash.app.data.Gear
import com.evdash.app.data.Severity
import com.evdash.app.data.TelemetrySnapshot
import com.evdash.app.ui.dashboard.DashboardViewModel
import com.evdash.app.ui.theme.Adaptive
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.DangerRed
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.ElectricCyanLight
import com.evdash.app.ui.theme.EvGreen
import com.evdash.app.ui.theme.OutlineLight
import com.evdash.app.ui.theme.SurfaceVariantLight
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh
import com.evdash.app.ui.theme.TextMid
import com.evdash.app.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    settingsVm: com.evdash.app.ui.settings.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val useMetric by settingsVm.useMetric.collectAsStateWithLifecycle()
    val useCelsius by settingsVm.useCelsius.collectAsStateWithLifecycle()
    StatusContent(
        telemetry = telemetry,
        useMetric = useMetric,
        useCelsius = useCelsius,
        modifier = modifier
    )
}

@Composable
private fun StatusContent(
    telemetry: TelemetrySnapshot,
    useMetric: Boolean,
    useCelsius: Boolean,
    modifier: Modifier = Modifier
) {
    val speedUnit = if (useMetric) "km/h" else "mph"
    val tempUnit = if (useCelsius) "°C" else "°F"
    val displaySpeed = if (useMetric) telemetry.speedKmh else telemetry.speedKmh * 0.621371f
    val displayCtrlTemp = if (useCelsius) telemetry.controllerTempC else telemetry.controllerTempC * 9f / 5f + 32f
    val displayMotorTemp = if (useCelsius) telemetry.motorTempC else telemetry.motorTempC * 9f / 5f + 32f

    val brush = remember {
        Brush.linearGradient(
            colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
            start = Offset(0f, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )
    }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = timeFormatter.format(Date())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
            .padding(Adaptive.pagePadding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Adaptive.smallSpacing)
        ) {
            // Header
            StatusHeader(time = currentTime, state = telemetry.connectionState)

            // Main gauge area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                SpeedGauge(
                    speedKmh = displaySpeed,
                    speedUnit = speedUnit,
                    rpm = telemetry.rpm,
                    maxRpm = 9000
                )
            }

            // Middle row: SoC + Gear + Power + Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocGauge(
                    soc = telemetry.socPercent,
                    modifier = Modifier.size(Adaptive.socSize)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GearIndicator(gear = telemetry.gear)
                    PowerBar(powerKw = telemetry.instantPowerKw)
                }
                RangeTile(
                    rangeKm = telemetry.estimatedRangeKm,
                    odometerKm = telemetry.odometerKm,
                    tripKm = telemetry.tripKm,
                    modifier = Modifier.width(Adaptive.rangeWidth)
                )
            }

            // Bottom status cards
            if (telemetry.errors.isNotEmpty()) {
                ErrorPanel(errors = telemetry.errors)
            } else {
                StatusCardsRow(
                    telemetry = telemetry,
                    displayMotorTemp = displayMotorTemp,
                    displayCtrlTemp = displayCtrlTemp,
                    tempUnit = tempUnit
                )
            }
        }
    }
}

@Composable
private fun StatusHeader(time: String, state: ConnectionState) {
    val (statusText, statusColor) = when (state) {
        ConnectionState.DISCONNECTED -> "未连接" to TextDim
        ConnectionState.SCANNING -> "扫描中..." to WarnAmber
        ConnectionState.CONNECTING -> "连接中..." to WarnAmber
        ConnectionState.CONNECTED -> "已连接" to EvGreen
        ConnectionState.DEMO -> "演示模式" to ElectricCyan
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "EV 仪表",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = Adaptive.headerSize),
            color = TextHigh
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleLarge,
                color = TextMid
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedGauge(
    speedKmh: Float,
    speedUnit: String,
    rpm: Int,
    maxRpm: Int,
    modifier: Modifier = Modifier
) {
    val maxSpeed = 120f
    val fraction = (speedKmh / maxSpeed).coerceIn(0f, 1f)
    val sweepAngle = fraction * 270f
    val animatedSpeed by animateFloatAsState(targetValue = speedKmh, label = "speed")
    val animatedSweep by animateFloatAsState(targetValue = sweepAngle, label = "sweep")

    val color = when {
        speedKmh > 100 -> DangerRed
        speedKmh > 80 -> WarnAmber
        else -> ElectricCyan
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val isTiny = Adaptive.isTiny
        val isCompact = Adaptive.isCompact
        val gaugeSize = minOf(maxWidth, maxHeight) * when {
            isTiny -> 0.85f
            isCompact -> 0.75f
            else -> 0.9f
        }
        val strokeDp = when {
            isTiny -> 6.dp
            isCompact -> 8.dp
            else -> 18.dp
        }
        val canvasPadding = when {
            isTiny -> 16.dp
            isCompact -> 12.dp
            else -> 0.dp
        }
        Box(
            modifier = Modifier.size(gaugeSize),
            contentAlignment = Alignment.Center
        ) {
            val strokePx = with(LocalDensity.current) { strokeDp.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(canvasPadding)
            ) {
                val arcSize = size.minDimension - strokePx * 2
                val topLeft = Offset(
                    (size.width - arcSize) / 2,
                    (size.height - arcSize) / 2
                )
                // Background arc
                drawArc(
                    color = SurfaceVariantLight,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                // Foreground arc
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(animatedSpeed),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = Adaptive.speedGaugeSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp
                    ),
                    color = TextHigh
                )
                Text(
                    text = speedUnit,
                    style = when {
                        isTiny -> MaterialTheme.typography.labelSmall
                        isCompact -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleLarge
                    },
                    color = TextMid
                )
                Spacer(modifier = Modifier.height(when {
                    isTiny -> 0.dp
                    isCompact -> 2.dp
                    else -> 4.dp
                }))
                val rpmFraction = (rpm.toFloat() / maxRpm).coerceIn(0f, 1f)
                val rpmColor = when {
                    rpmFraction > 0.9 -> DangerRed
                    rpmFraction > 0.75 -> WarnAmber
                    else -> TextDim
                }
                Text(
                    text = "$rpm rpm",
                    style = when {
                        isTiny -> MaterialTheme.typography.labelSmall
                        isCompact -> MaterialTheme.typography.bodySmall
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    color = rpmColor
                )
            }
        }
    }
}

@Composable
private fun SocGauge(soc: Float, modifier: Modifier = Modifier) {
    val sweepAngle = (soc / 100f) * 270f
    val color = when {
        soc < 20 -> DangerRed
        soc < 40 -> WarnAmber
        else -> EvGreen
    }

    val isCompact = Adaptive.isCompact
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(if (isCompact) 4.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = (if (isCompact) 8.dp else 12.dp).toPx()
            val arcSize = size.minDimension - stroke
            val topLeft = Offset(
                (size.width - arcSize) / 2,
                (size.height - arcSize) / 2
            )
            drawArc(
                color = SurfaceVariantLight,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.0f%%".format(soc),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (isCompact) 16.sp else 28.sp),
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "电量",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun RangeTile(
    rangeKm: Float,
    odometerKm: Float,
    tripKm: Float,
    modifier: Modifier = Modifier
) {
    val isCompact = Adaptive.isCompact
    Card(
        modifier = if (isCompact) modifier else modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 6.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = "%.1f".format(rangeKm),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (isCompact) 18.sp else 28.sp),
                color = EvGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "km 续航",
                style = MaterialTheme.typography.labelMedium,
                color = TextDim
            )
            if (!isCompact) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "总 %.0f km".format(odometerKm),
                style = MaterialTheme.typography.bodySmall,
                color = TextMid
            )
            Text(
                text = "小计 %.1f km".format(tripKm),
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun PowerBar(powerKw: Float, modifier: Modifier = Modifier) {
    val maxPower = 15f
    val fraction = (powerKw / maxPower).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "power")
    val color = when {
        powerKw > 12 -> DangerRed
        powerKw > 8 -> WarnAmber
        else -> ElectricCyan
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "%.2f kW".format(powerKw),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = if (Adaptive.isCompact) 16.sp else 20.sp),
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceVariantLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(ElectricCyanLight, color)
                        )
                    )
            )
        }
    }
}

@Composable
private fun GearIndicator(gear: Gear, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Gear.entries.forEach { g ->
            val active = g == gear
            val bgColor = when {
                active && g == Gear.BOOST -> DangerRed
                active -> ElectricCyan
                else -> SurfaceVariantLight
            }
            val textColor = if (active) Color.White else TextDim
            Box(
                modifier = Modifier
                    .size(width = if (Adaptive.isCompact) 32.dp else 40.dp, height = if (Adaptive.isCompact) 26.dp else 32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = g.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun StatusCardsRow(
    telemetry: TelemetrySnapshot,
    displayMotorTemp: Float,
    displayCtrlTemp: Float,
    tempUnit: String,
    modifier: Modifier = Modifier
) {
    val currentColor = when {
        telemetry.packCurrent > 200 -> DangerRed
        telemetry.packCurrent > 100 -> WarnAmber
        else -> ElectricCyan
    }
    val motorTempColor = when {
        telemetry.motorTempC >= 105 -> DangerRed
        telemetry.motorTempC >= 90 -> WarnAmber
        else -> ElectricCyan
    }
    val ctrlTempColor = when {
        telemetry.controllerTempC >= 90 -> DangerRed
        telemetry.controllerTempC >= 75 -> WarnAmber
        else -> ElectricCyan
    }

    val cards = listOf(
        Triple("电压", "%.1f V".format(telemetry.packVoltage), ElectricCyan),
        Triple("电流", "%.1f A".format(telemetry.packCurrent), currentColor),
        Triple("电机", "%.0f%s".format(displayMotorTemp, tempUnit), motorTempColor),
        Triple("控制器", "%.0f%s".format(displayCtrlTemp, tempUnit), ctrlTempColor)
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cards) { (label, value, color) ->
            Card(
                modifier = Modifier.width(Adaptive.statusCardWidth),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (Adaptive.isCompact) 8.dp else 12.dp, vertical = if (Adaptive.isCompact) 8.dp else 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorPanel(
    errors: List<com.evdash.app.data.ErrorCode>,
    modifier: Modifier = Modifier
) {
    if (errors.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⚠ 故障 (${errors.size})",
                style = MaterialTheme.typography.titleMedium,
                color = DangerRed,
                fontWeight = FontWeight.Bold
            )
            errors.forEach { err ->
                val color = when (err.severity) {
                    Severity.INFO -> TextMid
                    Severity.WARN -> WarnAmber
                    Severity.CRITICAL -> DangerRed
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = err.code,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        }
    }
}
