package com.evdash.app.ui.navmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evdash.app.data.location.LocationProvider
import com.evdash.app.data.map.MapRepository
import com.evdash.app.data.map.MapState
import com.evdash.app.data.map.UpdateState
import com.evdash.app.data.map.model.MapPackageMeta
import com.evdash.app.ui.navmap.search.PoiResult
import com.evdash.app.ui.navmap.search.PoiSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * NavMap 页的事实来源。
 *
 * 设计原则:
 * - 相机状态是 UI 的事实(MapView 在用户拖动时会改变 camera);VM 只在"重置回中心"或
 *   "follow=true 且收到新位置"时主动写,平时只承担"暴露当前是不是 follow"。
 * - update 状态由 [MapRepository] 接 WorkManager 反馈,VM 透传。
 * - location 流根据 [following] + 权限状态条件订阅,避免拒权时仍占用 GPS。
 */
@HiltViewModel
class NavMapViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val locationProvider: LocationProvider,
    private val poiSearchRepository: PoiSearchRepository
) : ViewModel() {

    private val _following = MutableStateFlow(true)
    val following: StateFlow<Boolean> = _following.asStateFlow()

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _recenterRequest = MutableStateFlow(0)
    val recenterRequest: StateFlow<Int> = _recenterRequest.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PoiResult>>(emptyList())
    val searchResults: StateFlow<List<PoiResult>> = _searchResults.asStateFlow()

    private val _selectedPoi = MutableStateFlow<PoiResult?>(null)
    val selectedPoi: StateFlow<PoiResult?> = _selectedPoi.asStateFlow()

    val uiState: StateFlow<NavMapUiState> = combine(
        mapRepository.activeSlotPath,
        mapRepository.observe(),
        _hasLocationPermission,
        _userLocation,
        _following
    ) { slotPath, mapState, hasPerm, loc, follow ->
        NavMapUiState(
            slotDir = slotPath,
            packageMeta = mapState.packageMeta,
            updateState = mapState.updateState,
            lastCheckedAt = mapState.lastCheckedAt,
            lastError = mapState.lastError,
            hasLocationPermission = hasPerm,
            userLocation = loc,
            following = follow
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NavMapUiState.Initial)

    private var locationJob: Job? = null

    init {
        viewModelScope.launch { mapRepository.refreshActiveSlot() }
    }

    fun onLocationPermissionGranted() {
        _hasLocationPermission.value = true
        startLocationUpdates()
    }

    fun onLocationPermissionDenied() {
        _hasLocationPermission.value = false
        locationJob?.cancel()
        _following.value = false
    }

    fun onMapViewReady() {
        if (locationProvider.hasFineLocationPermission()) {
            onLocationPermissionGranted()
        }
    }

    fun toggleFollow() {
        if (!_hasLocationPermission.value) return
        val next = !_following.value
        _following.value = next
        if (next) _recenterRequest.value = _recenterRequest.value + 1
    }

    fun requestRecenter() {
        if (_userLocation.value != null) {
            _recenterRequest.value = _recenterRequest.value + 1
        }
    }

    fun onUserGesture() {
        if (_following.value) _following.value = false
    }

    fun checkForUpdateNow() {
        mapRepository.enqueueImmediateUpdate()
    }

    fun onSearchQueryChange(query: String) {
        viewModelScope.launch {
            _searchResults.value = poiSearchRepository.search(query, _userLocation.value)
        }
    }

    fun onSearchResultPick(result: PoiResult) {
        _selectedPoi.value = result
        _searchResults.value = emptyList()
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { loc ->
                _userLocation.value = LatLng(loc.latitude, loc.longitude, loc.bearing)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}

data class NavMapUiState(
    val slotDir: File?,
    val packageMeta: MapPackageMeta?,
    val updateState: UpdateState,
    val lastCheckedAt: Long,
    val lastError: String?,
    val hasLocationPermission: Boolean,
    val userLocation: LatLng?,
    val following: Boolean
) {
    val mapReady: Boolean get() = slotDir != null
    val version: String get() = packageMeta?.version.orEmpty()
    val isUpdating: Boolean get() = updateState is UpdateState.Running || updateState is UpdateState.Pending

    companion object {
        val Initial = NavMapUiState(
            slotDir = null,
            packageMeta = null,
            updateState = UpdateState.Idle,
            lastCheckedAt = 0L,
            lastError = null,
            hasLocationPermission = false,
            userLocation = null,
            following = true
        )
    }
}

data class LatLng(val lat: Double, val lng: Double, val bearing: Float = 0f)
