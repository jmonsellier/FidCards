package com.monselio.fidcards.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortMode { RECENCY, NAME_ASC, NAME_DESC }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val sortModeKey = stringPreferencesKey("sort_mode")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val sortMode: Flow<SortMode> = context.dataStore.data.map { prefs ->
        prefs[sortModeKey]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
            ?: SortMode.RECENCY
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setSortMode(mode: SortMode) {
        context.dataStore.edit { it[sortModeKey] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }
}
