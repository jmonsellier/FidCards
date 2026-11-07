package com.monselio.fidcards.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monselio.fidcards.BuildConfig
import com.monselio.fidcards.R
import com.monselio.fidcards.backup.BackupManager
import com.monselio.fidcards.backup.BackupResult
import com.monselio.fidcards.backup.ImportMode
import com.monselio.fidcards.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoZoom by viewModel.autoZoom.collectAsStateWithLifecycle()
    val autoBackupUri by viewModel.autoBackupUri.collectAsStateWithLifecycle()
    val autoBackupHealthy by viewModel.autoBackupHealthy.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onImportPicked) }

    val autoBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::enableAutoBackup) }

    // Clé constante : le flux d'événements survit à la recomposition, donc la
    // snackbar a le temps de s'afficher jusqu'à son renvoi par l'utilisateur.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.results.collect { result ->
            snackbarHostState.showSnackbar(result.message(context))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            ThemeMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                    Text(
                        text = stringResource(mode.labelRes()),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_display))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_zoom)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_zoom_desc)) },
                trailingContent = {
                    Switch(
                        checked = autoZoom,
                        onCheckedChange = viewModel::setAutoZoom,
                    )
                },
                modifier = Modifier.clickable { viewModel.setAutoZoom(!autoZoom) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_backup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_desc)) },
                modifier = Modifier.clickable {
                    exportLauncher.launch(BackupManager.suggestedFileName())
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_desc)) },
                modifier = Modifier.clickable {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_backup)) },
                supportingContent = {
                    val failing = autoBackupUri != null && !autoBackupHealthy
                    Text(
                        text = autoBackupUri?.let { stored ->
                            val label = destinationLabel(stored)
                            if (failing) {
                                stringResource(R.string.settings_auto_backup_failed, label)
                            } else {
                                stringResource(R.string.settings_auto_backup_on, label)
                            }
                        } ?: stringResource(R.string.settings_auto_backup_off),
                        color = if (failing) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = autoBackupUri != null,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                autoBackupLauncher.launch("fidcards-auto.zip")
                            } else {
                                viewModel.disableAutoBackup()
                            }
                        },
                    )
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME))
                },
            )
        }
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.import_dialog_title)) },
            text = { Text(stringResource(R.string.import_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport(ImportMode.MERGE) }) {
                    Text(stringResource(R.string.import_merge))
                }
            },
            dismissButton = {
                // « Annuler » d'abord, à l'écart de l'action destructive, qui porte
                // en plus la couleur d'erreur : « Remplacer » efface la bibliothèque.
                Row {
                    TextButton(onClick = viewModel::cancelImport) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = { viewModel.confirmImport(ImportMode.REPLACE) }) {
                        Text(
                            text = stringResource(R.string.import_replace),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Un identifiant de document SAF ressemble à « primary:Download/fidcards.zip » :
 * l'afficher tel quel n'apprend rien, seul le nom de fichier est parlant.
 */
private fun destinationLabel(stored: String): String =
    stored.toUri().lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: stored

/** Message localisé d'un résultat de sauvegarde ; le `when` en garantit la couverture. */
private fun BackupResult.message(context: Context): String = when (this) {
    is BackupResult.ExportOk -> context.getString(R.string.backup_export_done, cards)
    is BackupResult.ImportOk -> context.getString(R.string.backup_import_done, imported, skipped)
    BackupResult.UnknownVersion -> context.getString(R.string.backup_error_version)
    BackupResult.Failed -> context.getString(R.string.backup_error_generic)
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
