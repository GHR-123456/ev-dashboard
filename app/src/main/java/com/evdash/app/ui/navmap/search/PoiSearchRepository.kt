package com.evdash.app.ui.navmap.search

import com.evdash.app.ui.navmap.LatLng
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3 占位:POI 搜索仓库。
 *
 * - **真实方案**:在离线包旁挂一份 SQLite,字段 `name / lat / lng / category / fts`,
 *   通过 FTS5 做前缀匹配 + 距离排序。
 * - **替代方案**:接 Nominatim / AMap POI / Mapbox Geocoding。
 *
 * 现在只暴露接口,返回硬编码示例。
 */
@Singleton
class PoiSearchRepository @Inject constructor() {

    suspend fun search(query: String, near: LatLng?): List<PoiResult> {
        if (query.isBlank()) return emptyList()
        return SAMPLES.filter { it.name.contains(query, ignoreCase = true) }
            .sortedBy {
                near?.let { n -> distance(n, it.location) } ?: Double.MAX_VALUE
            }
    }

    private fun distance(a: LatLng, b: LatLng): Double {
        val dLat = a.lat - b.lat
        val dLng = a.lng - b.lng
        return Math.sqrt(dLat * dLat + dLng * dLng)
    }

    private companion object {
        val SAMPLES = listOf(
            PoiResult("人民广场", LatLng(31.2330, 121.4757), "landmark"),
            PoiResult("陆家嘴金融中心", LatLng(31.2378, 121.5006), "landmark"),
            PoiResult("外滩观光", LatLng(31.2397, 121.4900), "landmark"),
            PoiResult("国家会展中心", LatLng(31.1925, 121.3036), "venue"),
            PoiResult("浦东国际机场", LatLng(31.1443, 121.8083), "transport"),
        )
    }
}

data class PoiResult(
    val name: String,
    val location: LatLng,
    val category: String
)
