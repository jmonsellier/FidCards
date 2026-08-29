package com.monselio.fidcards.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sauvegarde automatique : réécrit l'archive ZIP d'export vers un emplacement
 * SAF choisi une fois, à chaque modification des cartes (avec différé pour
 * grouper les rafales). L'app reste sans réseau : si l'emplacement vit chez un
 * fournisseur cloud (Drive, Dropbox) ou dans un dossier synchronisé
 * (Syncthing, Nextcloud), c'est cette app-là qui monte le fichier.
 *
 * Chaque tentative met à jour l'indicateur de santé lu par l'écran Réglages :
 * une destination devenue inaccessible doit se voir, pas se taire.
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
                // La destination a pu être révoquée depuis le dernier lancement :
                // le signaler tout de suite plutôt qu'au prochain export raté.
                settings.setAutoBackupHealthy(isWritable(uri))
                repository.observeAll()
                    // Ouvrir une carte n'écrit que des compteurs d'usage : ce
                    // n'est pas un changement de contenu et cela ne doit pas
                    // déclencher la réécriture complète de l'archive.
                    .map(::contentSignature)
                    .distinctUntilChanged()
                    // La première émission n'est pas un changement : pas de
                    // réécriture systématique à chaque lancement de l'app.
                    .drop(1)
                    .debounce(debounceMs)
                    .collect {
                        val result = backupManager.export(uri)
                        settings.setAutoBackupHealthy(result is BackupResult.ExportOk)
                    }
            }
        }
    }

    /**
     * Active la sauvegarde vers [uri] : permission persistante, premier export,
     * puis mémorisation. Sans permission persistante l'écriture survivrait à la
     * session en cours mais pas au redémarrage : refuser tout de suite plutôt
     * que d'afficher une sauvegarde active qui ne s'exécutera jamais.
     */
    suspend fun enable(uri: Uri): BackupResult {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
            }.isSuccess
            if (!granted) return BackupResult.Failed
        }
        val result = backupManager.export(uri)
        if (result !is BackupResult.ExportOk) return result
        settings.setAutoBackupUri(uri.toString())
        settings.setAutoBackupHealthy(true)
        return result
    }

    suspend fun disable() {
        settings.autoBackupUri.first()?.let { stored ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(stored.toUri(), permissionFlags)
            }
        }
        settings.setAutoBackupUri(null)
    }

    /** Une destination SAF dont la permission a sauté n'est plus écrivable. */
    private fun isWritable(uri: Uri): Boolean =
        uri.scheme != ContentResolver.SCHEME_CONTENT ||
            context.contentResolver.persistedUriPermissions
                .any { it.uri == uri && it.isWritePermission }

    /**
     * Ce qui distingue deux versions de l'archive, compteurs d'usage exclus :
     * l'identité et le contenu affiché de chaque carte.
     */
    private fun contentSignature(cards: List<Card>): List<List<Any?>> = cards.map {
        listOf(
            it.id,
            it.name,
            it.barcodeValue,
            it.displayValue,
            it.barcodeFormat,
            it.brandId,
            it.colorArgb,
            it.logoPath,
            it.frontImagePath,
            it.backImagePath,
        )
    }
}
