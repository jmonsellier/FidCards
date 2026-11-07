package com.monselio.fidcards.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.backup.AutoBackup
import com.monselio.fidcards.backup.BackupManager
import com.monselio.fidcards.backup.BackupResult
import com.monselio.fidcards.backup.ImportMode
import com.monselio.fidcards.settings.SettingsRepository
import com.monselio.fidcards.settings.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val backupManager: BackupManager,
    private val autoBackup: AutoBackup,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Destination de la sauvegarde automatique, nulle si désactivée. */
    val autoBackupUri: StateFlow<String?> = settings.autoBackupUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Faux si la dernière sauvegarde automatique a échoué (destination perdue). */
    val autoBackupHealthy: StateFlow<Boolean> = settings.autoBackupHealthy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun enableAutoBackup(uri: Uri) {
        viewModelScope.launch { _results.send(autoBackup.enable(uri)) }
    }

    fun disableAutoBackup() {
        viewModelScope.launch { autoBackup.disable() }
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val autoZoom: StateFlow<Boolean> = settings.autoZoom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Résultats d'export/import diffusés à l'écran pour la snackbar. Un canal et
     * non un StateFlow : un état consommé changerait la clé du LaunchedEffect qui
     * l'affiche, qui s'annulerait lui-même avant d'avoir montré quoi que ce soit.
     */
    private val _results = Channel<BackupResult>(Channel.BUFFERED)
    val results: Flow<BackupResult> = _results.receiveAsFlow()

    /** Uri d'un ZIP choisi, en attente du choix fusionner / remplacer. */
    private val _pendingImport = MutableStateFlow<Uri?>(null)
    val pendingImport: StateFlow<Uri?> = _pendingImport

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAutoZoom(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoZoom(enabled) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch { _results.send(backupManager.export(uri)) }
    }

    fun onImportPicked(uri: Uri) {
        _pendingImport.value = uri
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun confirmImport(mode: ImportMode) {
        val uri = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch { _results.send(backupManager.import(uri, mode)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FidCardsApplication
                SettingsViewModel(
                    app.container.backupManager,
                    app.container.autoBackup,
                    app.container.settingsRepository,
                )
            }
        }
    }
}
