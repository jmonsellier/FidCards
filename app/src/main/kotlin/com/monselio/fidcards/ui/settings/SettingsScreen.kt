package com.monselio.fidcards.ui.settings

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
import androidx.compose.ui.res.stringResource
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
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onImportPicked) }

    val messages = backupMessages()
    LaunchedEffect(lastResult) {
        val result = lastResult ?: return@LaunchedEffect
        viewModel.consumeResult()
        messages[result::class]?.let { render ->
            snackbarHostState.showSnackbar(render(result))
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
                Row {
                    TextButton(onClick = { viewModel.confirmImport(ImportMode.REPLACE) }) {
                        Text(stringResource(R.string.import_replace))
                    }
                    TextButton(onClick = viewModel::cancelImport) {
                        Text(stringResource(R.string.action_cancel))
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

/** Rend chaque résultat de sauvegarde en message localisé. */
@Composable
private fun backupMessages(): Map<kotlin.reflect.KClass<out BackupResult>, (BackupResult) -> String> {
    val exportOk = stringResource(R.string.backup_export_done)
    val importOk = stringResource(R.string.backup_import_done)
    val version = stringResource(R.string.backup_error_version)
    val failed = stringResource(R.string.backup_error_generic)
    return mapOf(
        BackupResult.ExportOk::class to { r -> exportOk.format((r as BackupResult.ExportOk).cards) },
        BackupResult.ImportOk::class to { r ->
            val ok = r as BackupResult.ImportOk
            importOk.format(ok.imported, ok.skipped)
        },
        BackupResult.UnknownVersion::class to { version },
        BackupResult.Failed::class to { failed },
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
