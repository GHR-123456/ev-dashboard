package com.evdash.app.worker

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.evdash.app.data.map.MapPackageManager
import com.evdash.app.data.map.MapPreferencesRepository
import com.evdash.app.data.map.MapRepository
import com.evdash.app.data.map.remote.MapUpdateApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * 拉清单 → 比版本 → 下载 → 校验 → 解压切槽。任何一步失败都按"保留旧槽 active"退场。
 *
 * 进度通过 [setProgress] 暴露给 UI(`MapRepository.observe()` 桥接);
 * 真实"下载中"分阶段:
 * - `manifest` :0%
 * - `download` :0–80%
 * - `verify`   :80–82%
 * - `unzip`    :82–98%
 * - `commit`   :100%
 */
@HiltWorker
class MapUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val api: MapUpdateApi,
    private val packageManager: MapPackageManager,
    private val prefs: MapPreferencesRepository,
    private val mapRepository: MapRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { prefs.markChecked(System.currentTimeMillis()) }

        return runCatching {
            setProgress(progressOf(0, "manifest"))
            val manifest = api.fetchManifest()
            val installed = prefs.currentMeta()
            if (installed != null && installed.version.compareTo(manifest.latestVersion) >= 0) {
                Log.i(TAG, "already up to date: ${installed.version}")
                return@runCatching Result.success(
                    workDataOf(
                        OUTPUT_VERSION to installed.version,
                        OUTPUT_UPGRADED to false
                    )
                )
            }
            if (!hasEnoughStorage(manifest.sizeBytes)) {
                throw IOException("存储不足:需要约 ${manifest.sizeBytes / 1_000_000} MB")
            }

            val partFile = packageManager.pendingDownloadFile(manifest.latestVersion)
            api.downloadPackage(manifest.packageUrl, partFile) { soFar, total ->
                val pct = if (total > 0L) (soFar * 80 / total).toInt() else 0
                setProgress(progressOf(pct.coerceIn(0, 80), "download"))
            }

            setProgress(progressOf(80, "verify"))
            val actualSha = packageManager.sha256(partFile)
            if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                partFile.delete()
                throw IOException("SHA-256 校验失败")
            }
            val finalized = packageManager.finalizeDownload(partFile)

            setProgress(progressOf(85, "unzip"))
            val targetSlot = MapPreferencesRepository.otherSlot(installed?.slot ?: MapPreferencesRepository.SLOT_NONE)
            val newMeta = packageManager.installFromZip(
                zipFile = finalized,
                version = manifest.latestVersion,
                sha256 = manifest.sha256,
                targetSlot = targetSlot
            )
            runCatching { finalized.delete() }

            setProgress(progressOf(100, "commit"))
            mapRepository.refreshActiveSlot()

            Result.success(
                workDataOf(
                    OUTPUT_VERSION to newMeta.version,
                    OUTPUT_UPGRADED to true
                )
            )
        }.getOrElse { t ->
            Log.w(TAG, "update failed", t)
            runCatching { prefs.markUpdateError(t.message ?: t::class.java.simpleName) }
            Result.failure(workDataOf(OUTPUT_ERROR to (t.message ?: "未知错误")))
        }
    }

    private fun hasEnoughStorage(packageBytes: Long): Boolean {
        val files = context.filesDir
        val stat = StatFs(files.absolutePath)
        val available = stat.availableBytes
        return available > packageBytes * 3 / 2
    }

    private fun progressOf(percent: Int, stage: String): Data = workDataOf(
        PROGRESS_PERCENT to percent,
        PROGRESS_STAGE to stage
    )

    companion object {
        const val PROGRESS_PERCENT = "progress_percent"
        const val PROGRESS_STAGE = "progress_stage"
        const val OUTPUT_VERSION = "output_version"
        const val OUTPUT_UPGRADED = "output_upgraded"
        const val OUTPUT_ERROR = "output_error"
        private const val TAG = "MapUpdateWorker"
    }
}
