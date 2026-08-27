package com.monselio.fidcards.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.monselio.fidcards.R
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.ui.home.colorFromName
import java.io.File

/** Couleurs proposées quand l'enseigne est absente du catalogue (docs/PROJET.md §5.3). */
private val SWATCHES = listOf(
    0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593, 0xFF1565C0, 0xFF00695C,
    0xFF2E7D32, 0xFFF9A825, 0xFFEF6C00, 0xFF4E342E, 0xFF37474F, 0xFF424242,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditCardScreen(
    cardId: Long?,
    scannedValue: String?,
    scannedFormat: String?,
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EditCardViewModel = viewModel(
        key = "edit-$cardId-$scannedValue",
        factory = EditCardViewModel.factory(cardId, scannedValue, scannedFormat),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.edit_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NameField(state, viewModel)

            if (state.groupSiblings.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            R.string.edit_group_info,
                            state.brand?.cardGroup.orEmpty(),
                            state.groupSiblings.joinToString { it.name },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            OutlinedTextField(
                value = state.barcodeValue,
                onValueChange = viewModel::onValueChange,
                label = { Text(stringResource(R.string.edit_value_label)) },
                isError = state.valueError,
                supportingText = if (state.valueError) {
                    { Text(stringResource(R.string.edit_value_error)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FormatField(state.format, viewModel::onFormatChange)

            OutlinedTextField(
                value = state.displayValue,
                onValueChange = viewModel::onDisplayValueChange,
                label = { Text(stringResource(R.string.edit_display_value_label)) },
                supportingText = { Text(stringResource(R.string.edit_display_value_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LogoPicker(state = state, viewModel = viewModel)

            if (state.brand == null && state.logoPath == null) {
                ColorPicker(
                    name = state.name,
                    selected = state.colorArgb,
                    onSelect = viewModel::onColorSelect,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(
                    text = stringResource(R.string.edit_save),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.edit_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun NameField(state: EditUiState, viewModel: EditCardViewModel) {
    Column {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.edit_name_label)) },
            isError = state.nameError,
            supportingText = if (state.nameError) {
                { Text(stringResource(R.string.edit_name_error)) }
            } else {
                { Text(stringResource(R.string.edit_name_help)) }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        if (state.suggestions.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    state.suggestions.forEach { brand ->
                        ListItem(
                            headlineContent = { Text(brand.name) },
                            supportingContent = {
                                val detail = listOfNotNull(brand.sector, brand.cardGroup)
                                    .joinToString(" · ")
                                if (detail.isNotEmpty()) Text(detail)
                            },
                            modifier = Modifier.clickable { viewModel.selectBrand(brand) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatField(current: BarcodeFormat, onSelect: (BarcodeFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_format_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BarcodeFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.name.replace('_', ' ')) },
                    onClick = {
                        expanded = false
                        onSelect(format)
                    },
                )
            }
        }
    }
}

/**
 * Logo choisi depuis la galerie via le Photo Picker — donc sans permission de
 * stockage (§7). Prioritaire sur le logo du catalogue et sur la pastille.
 */
@Composable
private fun LogoPicker(state: EditUiState, viewModel: EditCardViewModel) {
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onLogoPicked) }

    Column {
        Text(
            text = stringResource(R.string.edit_logo_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (state.logoPath != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White,
                    modifier = Modifier.size(64.dp),
                ) {
                    AsyncImage(
                        model = File(state.logoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
            TextButton(
                onClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Text(stringResource(R.string.edit_logo_pick))
            }
            if (state.logoPath != null) {
                TextButton(onClick = viewModel::removeLogo) {
                    Text(stringResource(R.string.edit_logo_remove))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(name: String, selected: Int?, onSelect: (Int?) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.edit_color_label),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            // « Auto » : couleur dérivée du nom, comme sur la vignette.
            Swatch(
                color = if (name.isBlank()) MaterialTheme.colorScheme.surfaceVariant else colorFromName(name),
                isSelected = selected == null,
                onClick = { onSelect(null) },
            )
            SWATCHES.forEach { argb ->
                Swatch(
                    color = Color(argb),
                    isSelected = selected == argb,
                    onClick = { onSelect(argb) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(3.dp, borderColor, CircleShape)
            .padding(5.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
    )
}
