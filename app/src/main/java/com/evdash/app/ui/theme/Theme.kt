package com.evdash.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

private val EvLightScheme = lightColorScheme(
    primary = ElectricCyan,
    onPrimary = White,
    primaryContainer = ElectricCyanLight,
    onPrimaryContainer = TextHigh,
    secondary = EvGreen,
    onSecondary = White,
    tertiary = WarnAmber,
    onTertiary = TextHigh,
    error = DangerRed,
    onError = White,
    background = BackgroundLight,
    onBackground = TextHigh,
    surface = SurfaceLight,
    onSurface = TextHigh,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextMid,
    outline = OutlineLight,
    outlineVariant = OutlineLight
)

@Composable
fun CarGradientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val brush = Brush.linearGradient(
        colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )
    Box(
        modifier = modifier.background(brush),
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}

@Composable
fun EvDashboardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EvLightScheme,
        typography = EvTypography,
        content = content
    )
}
