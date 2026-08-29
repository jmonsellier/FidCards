package com.monselio.fidcards.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val autoZoomKey = booleanPreferencesKey("auto_zoom")
    private val autoBackupUriKey = stringPreferencesKey("auto_backup_uri")
    private val autoBackupHealthyKey = booleanPreferencesKey("auto_backup_healthy")

    val sortMode: Flow<SortMode> = context.dataStore.data.map { prefs ->
        prefs[sortModeKey]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
            ?: SortMode.RECENCY
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    /** Ouvrir directement le code agrandi à l'ouverture d'une carte. */
    val autoZoom: Flow<Boolean> = context.dataStore.data.map { it[autoZoomKey] ?: false }

    /** Destination SAF de la sauvegarde automatique, nulle si désactivée. */
    val autoBackupUri: Flow<String?> = context.dataStore.data.map { it[autoBackupUriKey] }

    /**
     * Faux dès qu'une sauvegarde automatique échoue — destination révoquée,
     * effacée ou pleine. Sans cet indicateur l'échec resterait invisible :
     * l'export automatique n'a personne à qui rendre compte.
     */
    val autoBackupHealthy: Flow<Boolean> = context.dataStore.data.map {
        it[autoBackupHealthyKey] ?: true
    }

    suspend fun setSortMode(mode: SortMode) {
        context.dataStore.edit { it[sortModeKey] = mode.name }
    }

    suspend fun setAutoZoom(enabled: Boolean) {
        context.dataStore.edit { it[autoZoomKey] = enabled }
    }

    suspend fun setAutoBackupUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) {
                it.remove(autoBackupUriKey)
                it.remove(autoBackupHealthyKey)
            } else {
                it[autoBackupUriKey] = uri
            }
        }
    }

    suspend fun setAutoBackupHealthy(healthy: Boolean) {
        context.dataStore.edit { it[autoBackupHealthyKey] = healthy }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }
}
