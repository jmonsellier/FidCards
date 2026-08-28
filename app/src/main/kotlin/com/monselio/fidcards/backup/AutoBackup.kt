package com.monselio.fidcards.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sauvegarde automatique : réécrit l'archive ZIP d'export vers un emplacement
 * SAF choisi une fois, à chaque modification des cartes (avec différé pour
 * grouper les rafales). L'app reste sans réseau : si l'emplacement vit chez un
 * fournisseur cloud (Drive, Dropbox) ou dans un dossier synchronisé
 * (Syncthing, Nextcloud), c'est cette app-là qui monte le fichier.
 */
class AutoBackup(
    private val context: Context,
    private val settings: SettingsRepository,
    private val backupManager: BackupManager,
    private val repository: CardRepository,
    private val debounceMs: Long = 3_000,
) {

    private val permissionFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.autoBackupUri.collectLatest { stored ->
                val uri = stored?.toUri() ?: return@collectLatest
                repository.observeAll()
                    // La première émission n'est pas un changement : pas de
                    // réécriture systématique à chaque lancement de l'app.
                    .drop(1)
                    .debounce(debounceMs)
                    .collect { backupManager.export(uri) }
            }
        }
    }

    /**
     * Active la sauvegarde vers [uri] : permission persistante, mémorisation,
     * et premier export immédiat — son résultat confirme que la cible s'écrit.
     */
    suspend fun enable(uri: Uri): BackupResult {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, permissionFlags) }
        settings.setAutoBackupUri(uri.toString())
        return backupManager.export(uri)
    }

    suspend fun disable() {
        settings.autoBackupUri.first()?.let { stored ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(stored.toUri(), permissionFlags)
            }
        }
        settings.setAutoBackupUri(null)
    }
}
