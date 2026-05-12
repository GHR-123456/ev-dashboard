package com.evdash.app.ui.navmap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.evdash.app.data.map.UpdateState
import com.evdash.app.ui.theme.Adaptive

enum class MapStatus { Offline, Updating, UpToDate, Error }

@Composable
fun MapStatusChip(
    status: MapStatus,
    version: String,
    progress: Int,
    modifier: Modifier = Modifier
) {
    val isCompact = Adaptive.isCompact
    val (icon: ImageVector, label: String, color: Color) = when (status) {
        MapStatus.Offline -> Triple(Icons.Default.CloudOff, "离线", Color(0xFF9E9E9E))
        MapStatus.Updating -> Triple(Icons.Default.CloudDownload, "更新 $progress%", Color(0xFF1E88E5))
        MapStatus.UpToDate -> Triple(Icons.Default.CloudDone, "已最新", Color(0xFF43A047))
        MapStatus.Error -> Triple(Icons.Default.ErrorOutline, "更新失败", Color(0xFFE53935))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isCompact) label else "$label · $version",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

fun UpdateState.toChipStatus(hasPackage: Boolean, hasError: Boolean): MapStatus = when (this) {
    is UpdateState.Running, is UpdateState.Pending -> MapStatus.Updating
    is UpdateState.Failed -> MapStatus.Error
    is UpdateState.Success -> MapStatus.UpToDate
    UpdateState.Idle -> when {
        hasError -> MapStatus.Error
        hasPackage -> MapStatus.UpToDate
        else -> MapStatus.Offline
    }
}

fun UpdateState.progressPercent(): Int = when (this) {
    is UpdateState.Running -> percent
    is UpdateState.Pending -> 0
    else -> 0
}
