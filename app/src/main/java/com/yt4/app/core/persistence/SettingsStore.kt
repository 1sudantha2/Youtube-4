package com.yt4.app.core.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "yt4_settings")

/**
 * Lightweight DataStore for cheap user preferences.
 * (Cookies live in [SecureStore] — never here.)
 */
class SettingsStore(private val context: Context) {

    private companion object {
        val KEY_PREFERRED_HEIGHT = intPreferencesKey("preferred_video_height") // 0 = Auto
    }

    /** 0 means "Auto" (adaptive with stall-based fallback). */
    val preferredVideoHeight: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_PREFERRED_HEIGHT] ?: 0 }

    suspend fun setPreferredVideoHeight(heightPx: Int) {
        context.settingsDataStore.edit { it[KEY_PREFERRED_HEIGHT] = heightPx }
    }
}
