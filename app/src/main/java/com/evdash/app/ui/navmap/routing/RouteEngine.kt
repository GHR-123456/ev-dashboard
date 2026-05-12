package com.evdash.app.ui.navmap.routing

import com.evdash.app.ui.navmap.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P3 占位:路径规划引擎门面。
 *
 * - **真实方案**:绑定 GraphHopper Android 库,下载 `.ghz` 全国图(~400MB),
 *   在本地跑路径算法。GraphHopper 商用 License 注意。
 * - **替代方案**:接 Valhalla 后端 / AMap navi SDK / OSRM 自托管。
 *
 * 现在只暴露接口,等真实接入。
 */
@Singleton
class RouteEngine @Inject constructor() {

    private val _state = MutableStateFlow<RouteState>(RouteState.Idle)
    val state: StateFlow<RouteState> = _state.asStateFlow()

    /**
     * 规划路线。当前实现:返回一条直线连接 [from] → [to] 作为占位。
     * 真实接入后这里发起异步图查询。
     */
    suspend fun plan(from: LatLng, to: LatLng): Route {
        val route = Route(
            polyline = listOf(from, to),
            distanceMeters = haversine(from, to).toInt(),
            durationSeconds = (haversine(from, to) / 13.9).toInt(),
            maneuvers = emptyList()
        )
        _state.value = RouteState.Planned(route)
        return route
    }

    fun cancel() {
        _state.value = RouteState.Idle
    }

    private fun haversine(a: LatLng, b: LatLng): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = Math.sin(dLat / 2).let { it * it } +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}

data class Route(
    val polyline: List<LatLng>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val maneuvers: List<Maneuver>
)

data class Maneuver(
    val at: LatLng,
    val instruction: String,
    val distanceMeters: Int
)

sealed interface RouteState {
    data object Idle : RouteState
    data object Planning : RouteState
    data class Planned(val route: Route) : RouteState
    data class Failed(val reason: String) : RouteState
}
