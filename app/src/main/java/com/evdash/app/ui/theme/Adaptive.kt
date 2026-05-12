package com.evdash.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 响应式布局尺寸的集中表示。
 * 以 1280x800 为基准（常见车机平板），小于此视为紧凑模式。
 */
data class AdaptiveSizes(
    val isTiny: Boolean,
    val isCompact: Boolean,
    val pagePadding: Dp,
    val pageSpacing: Dp,
    val smallSpacing: Dp,
    val headerSize: TextUnit,
    val displaySize: TextUnit,
    val speedGaugeSize: TextUnit,
    val statusCardWidth: Dp,
    val socSize: Dp,
    val rangeWidth: Dp,
    val quickCardWidth: Dp,
    val iconButtonSize: Dp,
    val playIconSize: Dp,
    val navRailWidth: Dp,
    val showNavLabels: Boolean,
    val navIconSize: Dp,
)

/**
 * 响应式布局辅助：根据屏幕尺寸提供自适应的值。
 * 调用方仍可使用 `Adaptive.pagePadding` 等单值属性（向后兼容），
 * 也可使用 `Adaptive.current` 一次性获取整个尺寸集合，减少重复计算。
 */
object Adaptive {

    val current: AdaptiveSizes
        @Composable get() {
            val cfg = LocalConfiguration.current
            return remember(cfg.screenWidthDp, cfg.screenHeightDp) {
                val isTiny = cfg.screenWidthDp < 600 || cfg.screenHeightDp < 400
                val isCompact = cfg.screenWidthDp < 800 || cfg.screenHeightDp < 500
                AdaptiveSizes(
                    isTiny = isTiny,
                    isCompact = isCompact,
                    pagePadding = when { isTiny -> 8.dp; isCompact -> 12.dp; else -> 20.dp },
                    pageSpacing = when { isTiny -> 6.dp; isCompact -> 10.dp; else -> 16.dp },
                    smallSpacing = when { isTiny -> 4.dp; isCompact -> 6.dp; else -> 10.dp },
                    headerSize = when { isTiny -> 24.sp; isCompact -> 28.sp; else -> 32.sp },
                    displaySize = when { isTiny -> 36.sp; isCompact -> 48.sp; else -> 56.sp },
                    speedGaugeSize = when { isTiny -> 28.sp; isCompact -> 56.sp; else -> 96.sp },
                    statusCardWidth = when { isTiny -> 90.dp; isCompact -> 110.dp; else -> 120.dp },
                    socSize = when { isTiny -> 64.dp; isCompact -> 64.dp; else -> 90.dp },
                    rangeWidth = when { isTiny -> 84.dp; isCompact -> 84.dp; else -> 110.dp },
                    quickCardWidth = when { isTiny -> 80.dp; isCompact -> 94.dp; else -> 110.dp },
                    iconButtonSize = when { isTiny -> 40.dp; isCompact -> 52.dp; else -> 64.dp },
                    playIconSize = when { isTiny -> 20.dp; isCompact -> 26.dp; else -> 32.dp },
                    navRailWidth = when { isTiny -> 52.dp; isCompact -> 60.dp; else -> 76.dp },
                    showNavLabels = !isTiny,
                    navIconSize = when { isTiny -> 20.dp; isCompact -> 22.dp; else -> 24.dp },
                )
            }
        }

    val isTiny: Boolean @Composable get() = current.isTiny
    val isCompact: Boolean @Composable get() = current.isCompact
    val pagePadding: Dp @Composable get() = current.pagePadding
    val pageSpacing: Dp @Composable get() = current.pageSpacing
    val smallSpacing: Dp @Composable get() = current.smallSpacing
    val headerSize: TextUnit @Composable get() = current.headerSize
    val displaySize: TextUnit @Composable get() = current.displaySize
    val speedGaugeSize: TextUnit @Composable get() = current.speedGaugeSize
    val statusCardWidth: Dp @Composable get() = current.statusCardWidth
    val socSize: Dp @Composable get() = current.socSize
    val rangeWidth: Dp @Composable get() = current.rangeWidth
    val quickCardWidth: Dp @Composable get() = current.quickCardWidth
    val iconButtonSize: Dp @Composable get() = current.iconButtonSize
    val playIconSize: Dp @Composable get() = current.playIconSize
    val navRailWidth: Dp @Composable get() = current.navRailWidth
    val showNavLabels: Boolean @Composable get() = current.showNavLabels
    val navIconSize: Dp @Composable get() = current.navIconSize
}
