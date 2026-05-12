package com.evdash.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evdash.app.data.SettingsRepository
import com.evdash.app.data.map.MapRepository
import com.evdash.app.data.map.MapState
import com.evdash.app.data.map.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val mapRepository: MapRepository
) : ViewModel() {

    val useMetric: StateFlow<Boolean> =
        repo.useMetric.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val useCelsius: StateFlow<Boolean> =
        repo.useCelsius.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val use24h: StateFlow<Boolean> =
        repo.use24h.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val keepScreenOn: StateFlow<Boolean> =
        repo.keepScreenOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val debugMode: StateFlow<Boolean> =
        repo.debugMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val forceDemo: StateFlow<Boolean> =
        repo.forceDemo.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val mapState: StateFlow<MapState> = mapRepository.observe().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        MapState(
            packageMeta = null,
            updateState = UpdateState.Idle,
            lastCheckedAt = 0L,
            lastError = null
        )
    )

    fun setUseMetric(v: Boolean) = viewModelScope.launch { repo.setUseMetric(v) }
    fun setUseCelsius(v: Boolean) = viewModelScope.launch { repo.setUseCelsius(v) }
    fun setUse24h(v: Boolean) = viewModelScope.launch { repo.setUse24h(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(v) }
    fun setDebugMode(v: Boolean) = viewModelScope.launch { repo.setDebugMode(v) }
    fun setForceDemo(v: Boolean) = viewModelScope.launch { repo.setForceDemo(v) }

    fun checkForMapUpdateNow() = mapRepository.enqueueImmediateUpdate()
}
