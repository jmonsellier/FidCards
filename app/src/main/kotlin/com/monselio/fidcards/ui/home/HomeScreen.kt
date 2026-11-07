package com.monselio.fidcards.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monselio.fidcards.R
import com.monselio.fidcards.settings.SortMode

@Composable
fun HomeScreen(
    onCardClick: (Long) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCard,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_card))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HomeHeader(onOpenSettings = onOpenSettings)
            when {
                !uiState.loaded -> Unit

                !uiState.hasAnyCard -> EmptyState()

                else -> {
                    SearchField(query = query, onQueryChange = viewModel::setQuery)
                    MetaRow(
                        count = uiState.cards.size,
                        sortMode = uiState.sortMode,
                        onSelectSort = viewModel::setSortMode,
                    )
                    CardGrid(cards = uiState.cards, onCardClick = onCardClick)
                }
            }
        }
    }
}

/** Wordmark à gauche, réglages en pastille à droite. Le tri est dans [MetaRow]. */
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FidCardsGlyph(size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        CircleButton(onClick = onOpenSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_open),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        // 48 dp : cible tactile minimale, que Surface(onClick) n'impose pas
        // d'elle-même contrairement à IconButton.
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(top = 12.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** Compte de cartes à gauche, tri courant (et son menu) à droite. */
@Composable
private fun MetaRow(count: Int, sortMode: SortMode, onSelectSort: (SortMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.home_card_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Box {
            var expanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = stringResource(sortMode.shortLabelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.home_sort),
                    )
                }
            }
            SortDropdown(
                expanded = expanded,
                onDismiss = { expanded = false },
                current = sortMode,
                onSelect = onSelectSort,
            )
        }
    }
}

@Composable
private fun CardGrid(cards: List<HomeCard>, onCardClick: (Long) -> Unit) {
    if (cards.isEmpty()) {
        Text(
            text = stringResource(R.string.home_no_results),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(cards, key = { it.card.id }) { item ->
            CardTile(
                card = item.card,
                logoModel = item.logoModel,
                onClick = { onCardClick(item.card.id) },
                isPhoto = item.isPhoto,
                brandColorArgb = item.brandColorArgb,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SortDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: SortMode,
    onSelect: (SortMode) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(stringResource(mode.labelRes())) },
                leadingIcon = {
                    if (mode == current) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onDismiss()
                    onSelect(mode)
                },
            )
        }
    }
}

/**
 * Glyphe du wordmark : l'éventail de cartes de l'icône, dessiné avec les
 * couleurs du thème de l'app (un drawable-night suivrait le thème système).
 */
@Composable
private fun FidCardsGlyph(size: Dp) {
    val back = MaterialTheme.colorScheme.primaryContainer
    val front = MaterialTheme.colorScheme.primary
    val bars = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width / 96f
        withTransform({ rotate(degrees = -12f) }) {
            drawRoundRect(
                color = back,
                topLeft = Offset(25f * s, 30f * s),
                size = Size(46f * s, 30f * s),
                cornerRadius = CornerRadius(7f * s),
            )
        }
        withTransform({ rotate(degrees = 7f) }) {
            drawRoundRect(
                color = front,
                topLeft = Offset(25f * s, 38f * s),
                size = Size(46f * s, 30f * s),
                cornerRadius = CornerRadius(7f * s),
            )
            listOf(33f to 3f, 39f to 2f, 43f to 4f, 50f to 2f, 54f to 3f, 60f to 2f)
                .forEach { (x, w) ->
                    drawRect(
                        color = bars,
                        topLeft = Offset(x * s, 46f * s),
                        size = Size(w * s, 14f * s),
                    )
                }
        }
    }
}

private fun SortMode.labelRes(): Int = when (this) {
    SortMode.RECENCY -> R.string.sort_recency
    SortMode.NAME_ASC -> R.string.sort_name_asc
    SortMode.NAME_DESC -> R.string.sort_name_desc
}

private fun SortMode.shortLabelRes(): Int = when (this) {
    SortMode.RECENCY -> R.string.sort_recency_short
    SortMode.NAME_ASC -> R.string.sort_name_asc_short
    SortMode.NAME_DESC -> R.string.sort_name_desc_short
}
