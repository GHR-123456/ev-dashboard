package com.evdash.app.data.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/**
 * App 启动时跑一次,把异步初始化的"必须先到位"的事情挂出去:
 *
 * 1. 初始化 MapLibre native(不需要 API key,做的是 native lib 加载)
 * 2. 把出厂种子包搬到 `filesDir/maps/slot-A/`(已存在则跳过)
 * 3. 启动周期性更新任务(WorkManager 会自己处理已存在的情况)
 *
 * 1 必须在主线程同步完成(MapView 第一次 inflate 前);2/3 都丢到 IO scope。
 */
@Singleton
class MapBootstrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapRepository: MapRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun boot() {
        MapLibre.getInstance(context)
        scope.launch {
            val seed = mapRepository.ensureSeed()
            mapRepository.pruneOldSlots()
            mapRepository.ensurePeriodicUpdate()
            // D 方案:APK 不带 seed 时,首启立刻入队一次性下载(等 Wi-Fi),
            // 不必等 7 天周期触发。
            if (seed == null) {
                mapRepository.enqueueAutoFirstDownload()
            }
        }
    }
}
