package com.evdash.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "ev_settings")

/**
 * 全局用户偏好:单位、时间格式、调试开关等。所有开关都通过 DataStore 持久化,
 * 由 Hilt 注入到任意 ViewModel(Settings / Dashboard / Status 等)消费。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val useMetric: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_METRIC] ?: true }
    val useCelsius: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_CELSIUS] ?: true }
    val use24h: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_24H] ?: true }
    val keepScreenOn: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_KEEP_ON] ?: false }
    val debugMode: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_DEBUG] ?: false }
    val forceDemo: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_FORCE_DEMO] ?: false }

    suspend fun setUseMetric(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_METRIC] = value }
    }

    suspend fun setUseCelsius(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_CELSIUS] = value }
    }

    suspend fun setUse24h(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_24H] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_KEEP_ON] = value }
    }

    suspend fun setDebugMode(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_DEBUG] = value }
    }

    suspend fun setForceDemo(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_FORCE_DEMO] = value }
    }

    private companion object {
        val KEY_METRIC = booleanPreferencesKey("use_metric")
        val KEY_CELSIUS = booleanPreferencesKey("use_celsius")
        val KEY_24H = booleanPreferencesKey("use_24h")
        val KEY_KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_DEBUG = booleanPreferencesKey("debug_mode")
        val KEY_FORCE_DEMO = booleanPreferencesKey("force_demo")
    }
}
