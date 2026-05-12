package com.evdash.app.ui.navmap

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.map.UpdateState
import com.evdash.app.ui.navmap.components.MapStatusChip
import com.evdash.app.ui.navmap.components.RecenterFab
import com.evdash.app.ui.navmap.components.SearchBar
import com.evdash.app.ui.navmap.components.progressPercent
import com.evdash.app.ui.navmap.components.toChipStatus
import com.evdash.app.ui.theme.Adaptive
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh

/**
 * 导航页主屏。布局:
 *
 * ```
 * ┌────────────────────────────────────────────────────┐
 * │ 导航                                                │
 * │ ┌────────────────────────────────────────────────┐ │
 * │ │ [MapStatusChip]            [SearchBar (P3)]    │ │
 * │ │                                                │ │
 * │ │            MapView (fill)                      │ │
 * │ │                                                │ │
 * │ │                              [RecenterFab]     │ │
 * │ └────────────────────────────────────────────────┘ │
 * └────────────────────────────────────────────────────┘
 * ```
 *
 * 数据流:
 * - 权限 → VM.onLocationPermissionGranted / Denied
 * - 位置 → MapViewHolder.cameraTarget(只在 follow=true 时跟随)
 * - 切槽 → MapViewHolder.slotDir 变化 → 自动 setStyle
 */
@Composable
fun NavMapScreen(
    modifier: Modifier = Modifier,
    viewModel: NavMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recenterTrigger by viewModel.recenterRequest.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val sizes = Adaptive.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onLocationPermissionGranted()
        else viewModel.onLocationPermissionDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.onMapViewReady()
        if (!uiState.hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
            .padding(sizes.pagePadding),
        verticalArrangement = Arrangement.spacedBy(sizes.pageSpacing)
    ) {
        Text(
            text = "导航",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = sizes.headerSize),
            color = TextHigh
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val slot = uiState.slotDir
                if (slot != null) {
                    MapViewHolder(
                        slotDir = slot,
                        cameraTarget = uiState.userLocation,
                        follow = uiState.following && uiState.hasLocationPermission,
                        bearing = uiState.userLocation?.bearing ?: 0f,
                        recenterTrigger = recenterTrigger,
                        onMapReady = { viewModel.onMapViewReady() },
                        onUserGesture = { viewModel.onUserGesture() },
                        modifier = Modifier.fillMaxSize()
                    )

                    MapStatusChip(
                        status = uiState.updateState.toChipStatus(
                            hasPackage = uiState.packageMeta != null,
                            hasError = uiState.lastError != null
                        ),
                        version = uiState.version.ifBlank { "—" },
                        progress = uiState.updateState.progressPercent(),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )

                    SearchBar(
                        results = searchResults,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onPick = viewModel::onSearchResultPick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .width(260.dp)
                    )

                    RecenterFab(
                        hasPermission = uiState.hasLocationPermission,
                        following = uiState.following,
                        onClick = {
                            if (!uiState.hasLocationPermission) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                viewModel.toggleFollow()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                    )
                } else {
                    Placeholder(
                        updateState = uiState.updateState,
                        error = uiState.lastError
                    )
                }
            }
        }
    }
}

@Composable
private fun Placeholder(updateState: UpdateState, error: String?) {
    data class PlaceholderState(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val tint: Color,
        val title: String,
        val subtitle: String,
        val percent: Int?
    )

    val state = when (updateState) {
        UpdateState.Idle -> if (error != null) {
            PlaceholderState(Icons.Default.ErrorOutline, Color(0xFFE53935), "下载暂停", error, null)
        } else {
            PlaceholderState(
                Icons.Default.CloudOff,
                TextDim,
                "等待网络中",
                "首次启动需要下载离线地图包(约 400 MB)\n请连接 Wi-Fi,会在后台自动开始",
                null
            )
        }
        UpdateState.Pending -> PlaceholderState(
            Icons.Default.CloudDownload,
            ElectricCyan,
            "等待 Wi-Fi 可用…",
            "Wi-Fi 一旦连上会自动开始下载",
            null
        )
        is UpdateState.Running -> PlaceholderState(
            Icons.Default.CloudDownload,
            ElectricCyan,
            "正在下载地图",
            "${updateState.stage}  ·  ${updateState.percent}%",
            updateState.percent
        )
        is UpdateState.Failed -> PlaceholderState(
            Icons.Default.ErrorOutline,
            Color(0xFFE53935),
            "下载失败",
            updateState.reason,
            null
        )
        is UpdateState.Success -> PlaceholderState(
            Icons.Default.Map,
            ElectricCyan,
            "准备完成",
            "正在加载瓦片…",
            100
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = state.icon,
                contentDescription = null,
                tint = state.tint.copy(alpha = 0.7f),
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextHigh,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
                textAlign = TextAlign.Center
            )
            if (state.percent != null) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.width(280.dp),
                    color = ElectricCyan,
                    trackColor = TextDim.copy(alpha = 0.2f)
                )
            }
        }
    }
}
