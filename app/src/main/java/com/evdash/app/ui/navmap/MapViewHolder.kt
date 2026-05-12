package com.evdash.app.ui.navmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.geometry.LatLng as MlLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * AndroidView 包装 MapLibre `MapView`,把 GL 生命周期接到 Compose 的 [DisposableEffect]。
 *
 * 设计要点:
 * - `MapView` 不能跨 navigation 池化(GL context 不安全),因此 `remember` 范围保持在
 *   当前 NavMap 屏幕,dispose 时强制释放。
 * - 切槽时 [slotDir] 变化 → 触发 `setStyle()` 重建,旧 style 由 MapLibre 自动 dispose。
 * - 用户拖动地图时调 [onUserGesture],让 VM 取消 follow 模式。
 */
@Composable
fun MapViewHolder(
    slotDir: File,
    cameraTarget: LatLng?,
    follow: Boolean,
    bearing: Float,
    recenterTrigger: Int,
    onMapReady: () -> Unit,
    onUserGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(ctx).apply { onCreate(null) } }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { libreMap ->
                    val styleJson = loadStyle(File(slotDir, "style.json"), slotDir)
                    libreMap.setStyle(Style.Builder().fromJson(styleJson)) {
                        libreMap.uiSettings.isAttributionEnabled = false
                        libreMap.uiSettings.isLogoEnabled = false
                        libreMap.cameraPosition = defaultCamera()

                        libreMap.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                onUserGesture()
                            }
                            override fun onMove(detector: MoveGestureDetector) = Unit
                            override fun onMoveEnd(detector: MoveGestureDetector) = Unit
                        })

                        onMapReady()
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { libreMap ->
                applyCamera(libreMap, cameraTarget, follow, bearing)
            }
        },
        modifier = modifier
    )

    LaunchedEffect(recenterTrigger, cameraTarget) {
        if (cameraTarget != null) {
            mapView.getMapAsync { map ->
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(MlLatLng(cameraTarget.lat, cameraTarget.lng))
                            .zoom(if (follow) FOLLOW_ZOOM else DEFAULT_ZOOM)
                            .bearing(if (follow) bearing.toDouble() else 0.0)
                            .build()
                    )
                )
            }
        }
    }

    DisposableEffect(slotDir) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(loadStyle(File(slotDir, "style.json"), slotDir)))
        }
        onDispose { }
    }
}

private fun applyCamera(
    map: MapLibreMap,
    target: LatLng?,
    follow: Boolean,
    bearing: Float
) {
    if (target == null) return
    if (!follow) return
    map.cameraPosition = CameraPosition.Builder()
        .target(MlLatLng(target.lat, target.lng))
        .zoom(FOLLOW_ZOOM)
        .bearing(bearing.toDouble())
        .build()
}

/**
 * 把 style.json 中的 `mbtiles://tiles` 占位替换成实际 file:// URI。
 *
 * 这样 style.json 文件本身不需要绑定到具体的安装路径,跨槽位切换不用每次重写文件。
 */
private fun loadStyle(styleFile: File, slotDir: File): String {
    val raw = styleFile.readText()
    val tilesAbs = File(slotDir, "tiles.mbtiles").absolutePath
    val obj = JSONObject(raw)
    val sources = obj.optJSONObject("sources") ?: return raw
    val keys = sources.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val src = sources.getJSONObject(key)
        val tiles = src.optJSONArray("tiles")
        if (tiles != null) {
            for (i in 0 until tiles.length()) {
                val url = tiles.optString(i)
                if (url.startsWith("mbtiles://")) {
                    tiles.put(i, "mbtiles://$tilesAbs")
                }
            }
        }
        val srcUrl = src.optString("url", "")
        if (srcUrl.startsWith("mbtiles://")) {
            src.put("url", "mbtiles://$tilesAbs")
        }
    }
    return obj.toString()
}

private fun defaultCamera(): CameraPosition = CameraPosition.Builder()
    .target(MlLatLng(31.2304, 121.4737))
    .zoom(DEFAULT_ZOOM)
    .build()

private const val DEFAULT_ZOOM = 11.0
private const val FOLLOW_ZOOM = 15.0
