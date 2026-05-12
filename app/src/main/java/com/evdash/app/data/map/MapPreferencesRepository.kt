package com.evdash.app.data.map

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.evdash.app.data.map.model.MapPackageMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mapDataStore by preferencesDataStore(name = "ev_map")

/**
 * 与 `ev_settings` 隔离的独立 DataStore,专门保存地图包元数据。
 *
 * 字段语义见 [Keys] —— `activeSlot` 的写入是整套原子切槽流程的 commit point。
 */
@Singleton
class MapPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val activeSlot: Flow<String> = context.mapDataStore.data.map { it[Keys.ACTIVE_SLOT] ?: SLOT_NONE }
    val installedVersion: Flow<String> = context.mapDataStore.data.map { it[Keys.VERSION] ?: VERSION_NONE }
    val sizeBytes: Flow<Long> = context.mapDataStore.data.map { it[Keys.SIZE_BYTES] ?: 0L }
    val installedAt: Flow<Long> = context.mapDataStore.data.map { it[Keys.INSTALLED_AT] ?: 0L }
    val sha256: Flow<String> = context.mapDataStore.data.map { it[Keys.SHA256] ?: "" }
    val source: Flow<MapPackageMeta.Source> = context.mapDataStore.data.map {
        it[Keys.SOURCE]?.let(MapPackageMeta.Source::valueOf) ?: MapPackageMeta.Source.SEED
    }
    val lastCheckedAt: Flow<Long> = context.mapDataStore.data.map { it[Keys.LAST_CHECKED_AT] ?: 0L }
    val lastUpdateError: Flow<String?> = context.mapDataStore.data.map { it[Keys.LAST_ERROR] }

    val meta: Flow<MapPackageMeta?> = context.mapDataStore.data.map { prefs -> prefs.toMeta() }

    suspend fun currentMeta(): MapPackageMeta? = context.mapDataStore.data.first().toMeta()

    suspend fun commitPackage(meta: MapPackageMeta) {
        context.mapDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SLOT] = meta.slot
            prefs[Keys.VERSION] = meta.version
            prefs[Keys.SIZE_BYTES] = meta.sizeBytes
            prefs[Keys.INSTALLED_AT] = meta.installedAt
            prefs[Keys.SHA256] = meta.sha256
            prefs[Keys.SOURCE] = meta.source.name
            prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun markChecked(timestamp: Long) {
        context.mapDataStore.edit { it[Keys.LAST_CHECKED_AT] = timestamp }
    }

    suspend fun markUpdateError(reason: String) {
        context.mapDataStore.edit { it[Keys.LAST_ERROR] = reason }
    }

    suspend fun clearUpdateError() {
        context.mapDataStore.edit { it.remove(Keys.LAST_ERROR) }
    }

    private fun Preferences.toMeta(): MapPackageMeta? {
        val slot = this[Keys.ACTIVE_SLOT] ?: return null
        if (slot == SLOT_NONE) return null
        return MapPackageMeta(
            slot = slot,
            version = this[Keys.VERSION] ?: VERSION_NONE,
            sizeBytes = this[Keys.SIZE_BYTES] ?: 0L,
            installedAt = this[Keys.INSTALLED_AT] ?: 0L,
            sha256 = this[Keys.SHA256] ?: "",
            source = this[Keys.SOURCE]?.let(MapPackageMeta.Source::valueOf) ?: MapPackageMeta.Source.SEED
        )
    }

    private object Keys {
        val ACTIVE_SLOT = stringPreferencesKey("active_slot")
        val VERSION = stringPreferencesKey("installed_version")
        val SIZE_BYTES = longPreferencesKey("installed_size_bytes")
        val INSTALLED_AT = longPreferencesKey("installed_at_millis")
        val SHA256 = stringPreferencesKey("installed_sha256")
        val SOURCE = stringPreferencesKey("installed_source")
        val LAST_CHECKED_AT = longPreferencesKey("last_checked_at_millis")
        val LAST_ERROR = stringPreferencesKey("last_update_error")
    }

    companion object {
        const val SLOT_A = "slot-A"
        const val SLOT_B = "slot-B"
        const val SLOT_NONE = ""
        const val VERSION_NONE = ""

        fun otherSlot(active: String): String = when (active) {
            SLOT_A -> SLOT_B
            SLOT_B -> SLOT_A
            else -> SLOT_A
        }
    }
}
