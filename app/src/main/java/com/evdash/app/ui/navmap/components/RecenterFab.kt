package com.evdash.app.ui.navmap.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.evdash.app.ui.theme.Adaptive
import com.evdash.app.ui.theme.ElectricCyan

/**
 * 右下角 FAB,三种态:
 * - 无权限 → 灰色禁用,点击触发权限再申请回调
 * - 有权限 + 非跟随 → 蓝色,点击切回 follow
 * - 有权限 + 跟随中 → 高亮,点击仅刷新一次居中
 */
@Composable
fun RecenterFab(
    hasPermission: Boolean,
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizes = Adaptive.current
    val (icon, container) = when {
        !hasPermission -> Icons.Default.LocationDisabled to Color.Gray.copy(alpha = 0.6f)
        following -> Icons.Default.NearMe to ElectricCyan
        else -> Icons.Default.MyLocation to MaterialTheme.colorScheme.primaryContainer
    }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(sizes.iconButtonSize),
        containerColor = container,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "重新居中"
        )
    }
}
