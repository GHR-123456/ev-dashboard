package com.evdash.app.data.map

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.evdash.app.data.map.model.MapPackageMeta
import com.evdash.app.worker.MapUpdateWorker
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * 给 UI 层用的"地图状态门面"。
 *
 * 内部聚合三件事:
 * 1. [activeSlot] :当前激活槽位的目录(给 MapView 加载 mbtiles/style.json)
 * 2. [meta] / [version] :当前包元数据(Settings 页展示)
 * 3. [updateState] :最近一次更新任务的进度
 */
@Singleton
class MapRepository @Inject constructor(
    private val packageManager: MapPackageManager,
    private val prefs: MapPreferencesRepository,
    private val workManager: WorkManager
) {
    private val _activeSlotPath = MutableStateFlow<File?>(null)
    val activeSlotPath: Flow<File?> = _activeSlotPath.asStateFlow()

    val meta: Flow<MapPackageMeta?> = prefs.meta
    val version: Flow<String> = prefs.installedVersion
    val lastCheckedAt: Flow<Long> = prefs.lastCheckedAt
    val lastError: Flow<String?> = prefs.lastUpdateError
    val source: Flow<MapPackageMeta.Source> = prefs.source

    /** UI 层订阅的总状态:meta + 最近一次更新状态(从 WorkManager 拉)。 */
    fun observe(): Flow<MapState> = combine(
        meta,
        lastError,
        lastCheckedAt,
        observeUpdateState()
    ) { meta, error, checkedAt, update ->
        MapState(
            packageMeta = meta,
            updateState = update,
            lastCheckedAt = checkedAt,
            lastError = error
        )
    }

    /** 首次启动时调用:确保磁盘上至少有种子包,并刷新 [activeSlotPath]。返回 null 表示无 seed,调用方应触发联网下载。 */
    suspend fun ensureSeed(): com.evdash.app.data.map.model.MapPackageMeta? {
        val meta = packageManager.ensureSeed()
        refreshActiveSlot()
        return meta
    }

    suspend fun refreshActiveSlot() {
        _activeSlotPath.value = packageManager.activeSlotDir()
    }

    suspend fun pruneOldSlots() {
        packageManager.pruneInactiveSlot()
        refreshActiveSlot()
    }

    /** 用户在 Settings 页点"立即检查更新"时调用。允许 metered 网络。 */
    fun enqueueImmediateUpdate() {
        val req = OneTimeWorkRequestBuilder<MapUpdateWorker>()
            .setConstraints(anyNetworkConstraint())
            .build()
        workManager.enqueueUniqueWork(WORK_UPDATE_ONESHOT, ExistingWorkPolicy.REPLACE, req)
    }

    /**
     * 首启发现无 seed 时调用:挂一次性下载,但等 UNMETERED Wi-Fi 才跑。
     *
     * 与 [enqueueImmediateUpdate] 的区别在网络约束:这是"自动后台",所以保守约束;
     * 用户在 Settings 主动点是"明确同意",可以走 metered。
     */
    fun enqueueAutoFirstDownload() {
        val req = OneTimeWorkRequestBuilder<MapUpdateWorker>()
            .setConstraints(wifiOnlyConstraint())
            .build()
        workManager.enqueueUniqueWork(WORK_UPDATE_ONESHOT, ExistingWorkPolicy.KEEP, req)
    }

    /** App 启动时调用一次:把 7 天周期任务挂到 WorkManager(已有则 KEEP)。 */
    fun ensurePeriodicUpdate() {
        val req = PeriodicWorkRequestBuilder<MapUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(wifiOnlyConstraint())
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_UPDATE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    private fun observeUpdateState(): Flow<UpdateState> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_UPDATE_ONESHOT)
            .map { infos -> infos.toUpdateState() }
            .onStart { emit(UpdateState.Idle) }

    private fun List<WorkInfo>.toUpdateState(): UpdateState {
        val latest = maxByOrNull { it.runAttemptCount } ?: return UpdateState.Idle
        return when (latest.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UpdateState.Pending
            WorkInfo.State.RUNNING -> {
                val pct = latest.progress.getInt(MapUpdateWorker.PROGRESS_PERCENT, 0)
                val stage = latest.progress.getString(MapUpdateWorker.PROGRESS_STAGE) ?: ""
                UpdateState.Running(pct, stage)
            }
            WorkInfo.State.SUCCEEDED -> UpdateState.Success(
                latest.outputData.getString(MapUpdateWorker.OUTPUT_VERSION) ?: ""
            )
            WorkInfo.State.FAILED -> UpdateState.Failed(
                latest.outputData.getString(MapUpdateWorker.OUTPUT_ERROR) ?: "未知错误"
            )
            WorkInfo.State.CANCELLED -> UpdateState.Idle
        }
    }

    private fun anyNetworkConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun wifiOnlyConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    companion object {
        const val WORK_UPDATE_ONESHOT = "map_update_oneshot"
        const val WORK_UPDATE_PERIODIC = "map_update_periodic"
    }
}

data class MapState(
    val packageMeta: MapPackageMeta?,
    val updateState: UpdateState,
    val lastCheckedAt: Long,
    val lastError: String?
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Pending : UpdateState
    data class Running(val percent: Int, val stage: String) : UpdateState
    data class Success(val version: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}
