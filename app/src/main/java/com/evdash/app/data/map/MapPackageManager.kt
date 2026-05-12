package com.evdash.app.data.map

import android.content.Context
import android.util.Log
import com.evdash.app.data.map.model.MapPackageMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 负责把离线地图包真正落到磁盘上:
 *
 * - [ensureSeed] :首次启动时把 `assets/maps/seed.mbtiles` + `style.json` 拷到 `slot-A`
 * - [installFromZip] :给 worker 用,解压新下载的 `.zip` 到目标槽并校验
 * - [activeSlotDir] :给 ViewModel 用,拿到当前激活槽位的目录
 * - [pruneInactiveSlot] :冷启动时清理上一版本(保留一次回滚机会的策略由调用方控制)
 */
@Singleton
class MapPackageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: MapPreferencesRepository
) {
    private val mapsRoot: File = File(context.filesDir, "maps")
    val tmpDir: File = File(mapsRoot, "tmp")

    fun slotDir(slot: String): File = File(mapsRoot, slot)

    suspend fun activeSlotDir(): File? {
        val meta = prefs.currentMeta() ?: return null
        return slotDir(meta.slot).takeIf { it.exists() }
    }

    /**
     * 把 `assets/maps/seed.mbtiles` + `style.json` 拷到 slot-A。
     *
     * - 已有元数据 → 直接返回
     * - 没有元数据但 `seed.mbtiles` 不在 assets → 返回 null,UI 停留在占位屏,
     *   等待 P2 在线下载真实包(`assets/maps/README.md` 解释了为什么会出现这种情况)
     */
    suspend fun ensureSeed(): MapPackageMeta? {
        prefs.currentMeta()?.let { return it }

        if (!isAssetPresent("maps/seed.mbtiles")) {
            Log.w(TAG, "seed.mbtiles 缺失,跳过 bootstrap(等待远端包)")
            return null
        }

        val dest = slotDir(MapPreferencesRepository.SLOT_A)
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()

        val seedTiles = File(dest, TILES_FILE_NAME)
        val styleJson = File(dest, STYLE_FILE_NAME)

        copyAsset("maps/seed.mbtiles", seedTiles)
        copyAsset("maps/style.json", styleJson)

        val meta = MapPackageMeta(
            slot = MapPreferencesRepository.SLOT_A,
            version = SEED_VERSION,
            sizeBytes = seedTiles.length() + styleJson.length(),
            installedAt = System.currentTimeMillis(),
            sha256 = sha256(seedTiles),
            source = MapPackageMeta.Source.SEED
        )
        prefs.commitPackage(meta)
        Log.i(TAG, "seed installed: $meta")
        return meta
    }

    /**
     * 解压 [zipFile] 到 [targetSlot],并把版本元数据写入 DataStore(commit point)。
     *
     * 调用前必须:
     * 1. 已经下载完成且 SHA-256 校验通过的 zip 文件
     * 2. [targetSlot] != active slot(由 worker 调度保证)
     */
    suspend fun installFromZip(
        zipFile: File,
        version: String,
        sha256: String,
        targetSlot: String
    ): MapPackageMeta {
        val dest = slotDir(targetSlot)
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()
        unzipInto(zipFile, dest)

        val tiles = File(dest, TILES_FILE_NAME)
        if (!tiles.exists()) throw IOException("package missing $TILES_FILE_NAME")
        val style = File(dest, STYLE_FILE_NAME)
        if (!style.exists()) throw IOException("package missing $STYLE_FILE_NAME")

        val meta = MapPackageMeta(
            slot = targetSlot,
            version = version,
            sizeBytes = dest.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            installedAt = System.currentTimeMillis(),
            sha256 = sha256,
            source = MapPackageMeta.Source.REMOTE
        )
        prefs.commitPackage(meta)
        Log.i(TAG, "remote installed: $meta")
        return meta
    }

    /** 删除 active 之外的槽位,腾出空间给下一次更新。 */
    suspend fun pruneInactiveSlot() {
        val active = prefs.currentMeta()?.slot ?: return
        listOf(MapPreferencesRepository.SLOT_A, MapPreferencesRepository.SLOT_B)
            .filter { it != active }
            .map(::slotDir)
            .filter(File::exists)
            .forEach { if (!it.deleteRecursively()) Log.w(TAG, "prune failed: $it") }
    }

    /** 计算未来下载所需的临时 part 文件路径。 */
    fun pendingDownloadFile(version: String): File {
        if (!tmpDir.exists()) tmpDir.mkdirs()
        return File(tmpDir, "pkg-$version.zip.part")
    }

    /** 把 part 文件升级为正式文件:校验通过后改名。 */
    fun finalizeDownload(part: File): File {
        val finalized = File(part.parentFile, part.name.removeSuffix(".part"))
        if (finalized.exists()) finalized.delete()
        if (!part.renameTo(finalized)) throw IOException("rename ${part.name}")
        return finalized
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyAsset(assetPath: String, dest: File) {
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun isAssetPresent(assetPath: String): Boolean = runCatching {
        context.assets.open(assetPath).use { /* presence check */ }
        true
    }.getOrDefault(false)

    private fun unzipInto(zipFile: File, dest: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(dest, entry.name).normalize()
                if (!outFile.canonicalPath.startsWith(dest.canonicalPath)) {
                    throw IOException("zip path traversal: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
            }
        }
    }

    companion object {
        const val TILES_FILE_NAME = "tiles.mbtiles"
        const val STYLE_FILE_NAME = "style.json"
        const val SEED_VERSION = "seed-2026.05"
        private const val TAG = "MapPackageManager"
    }
}
