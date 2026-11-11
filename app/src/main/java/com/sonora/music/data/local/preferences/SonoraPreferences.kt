package com.sonora.music.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sonora.music.data.model.AudioQuality
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_preferences")

class SonoraPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_PLAYED_SONG_ID = longPreferencesKey("last_played_song_id")
        val LAST_PLAYED_POSITION_MS = longPreferencesKey("last_played_position_ms")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SHORT_TRACK_MAX_MS = longPreferencesKey("short_track_max_ms")
    }

    val shortTrackMaxDurationMsFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHORT_TRACK_MAX_MS] ?: 120_000L
    }

    suspend fun setShortTrackMaxDurationMs(durationMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHORT_TRACK_MAX_MS] = durationMs.coerceIn(15_000L, 600_000L)
        }
    }

    val downloadQualityFlow: Flow<AudioQuality> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.DOWNLOAD_QUALITY] ?: AudioQuality.HIGH_320.name
        runCatching { AudioQuality.valueOf(raw) }.getOrDefault(AudioQuality.HIGH_320)
    }

    suspend fun setDownloadQuality(quality: AudioQuality) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_QUALITY] = quality.name
        }
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    val sortOptionFlow: Flow<SortOption> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SORT_OPTION] ?: SortOption.TITLE.name
        runCatching { SortOption.valueOf(raw) }.getOrDefault(SortOption.TITLE)
    }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SORT_OPTION] = option.name
        }
    }

    val sortOrderFlow: Flow<SortOrder> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SORT_ORDER] ?: SortOrder.ASCENDING.name
        runCatching { SortOrder.valueOf(raw) }.getOrDefault(SortOrder.ASCENDING)
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SORT_ORDER] = order.name
        }
    }

    val isHistoryEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HISTORY_ENABLED] ?: true
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HISTORY_ENABLED] = enabled
        }
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    val lastPlayedSongIdFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_PLAYED_SONG_ID] ?: -1L
    }

    val lastPlayedPositionFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_PLAYED_POSITION_MS] ?: 0L
    }

    suspend fun saveLastPlaybackState(songId: Long, positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYED_SONG_ID] = songId
            prefs[Keys.LAST_PLAYED_POSITION_MS] = positionMs
        }
    }
}
