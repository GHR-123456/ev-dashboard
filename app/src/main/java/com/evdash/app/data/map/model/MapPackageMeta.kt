package com.evdash.app.data.map.model

/**
 * 描述当前活跃离线地图包的元数据。
 *
 * 字段全部从 `MapPreferencesRepository` 读出,
 * 真实文件存放在 `filesDir/maps/<slot>/` 下。
 */
data class MapPackageMeta(
    val slot: String,
    val version: String,
    val sizeBytes: Long,
    val installedAt: Long,
    val sha256: String,
    val source: Source
) {
    enum class Source {
        /** 出厂随 APK 提供的种子包(`assets/maps/seed.mbtiles`) */
        SEED,
        /** 通过联网下载安装的版本 */
        REMOTE
    }
}
