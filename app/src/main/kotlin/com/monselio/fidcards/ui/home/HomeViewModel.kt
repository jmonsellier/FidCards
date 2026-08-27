package com.monselio.fidcards.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.brands.BrandCatalog
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.data.RecencyScore
import com.monselio.fidcards.settings.SettingsRepository
import com.monselio.fidcards.settings.SortMode
import com.monselio.fidcards.util.searchNormalized
import java.io.File
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une carte prête à afficher : logo résolu (fichier perso ou asset du catalogue). */
data class HomeCard(
    val card: Card,
    val logoModel: Any?,
    /** Cartouche sombre exigé par le logo (§6.3.1), indépendamment du thème. */
    val logoOnDark: Boolean,
    /** Photo recto en repli (§5.1) : plein cadre, pas de cartouche. */
    val isPhoto: Boolean = false,
)

data class HomeUiState(
    val cards: List<HomeCard> = emptyList(),
    /** Vrai dès que la première émission de la base est arrivée. */
    val loaded: Boolean = false,
    /** Vrai si la base contient au moins une carte, indépendamment du filtre. */
    val hasAnyCard: Boolean = false,
    val sortMode: SortMode = SortMode.RECENCY,
)

class HomeViewModel(
    private val repository: CardRepository,
    private val settings: SettingsRepository,
    private val catalog: BrandCatalog,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    private val collator: Collator = Collator.getInstance(Locale.FRENCH)

    val uiState: StateFlow<HomeUiState> =
        combine(repository.observeAll(), query, settings.sortMode) { cards, q, sort ->
            HomeUiState(
                cards = sort(filter(cards, q), sort).map(::toHomeCard),
                loaded = true,
                hasAnyCard = cards.isNotEmpty(),
                sortMode = sort,
            )
        }
            // Le catalogue se charge des assets au premier accès : hors du main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Priorité de la vignette (§5.1) : logo, sinon photo recto, sinon pastille. */
    private fun toHomeCard(card: Card): HomeCard {
        card.logoPath?.let { path ->
            return HomeCard(card, File(path), logoOnDark = false)
        }
        val brand = catalog.byId(card.brandId)
        catalog.logoAssetUri(brand)?.let { uri ->
            return HomeCard(card, uri, logoOnDark = brand?.backgroundDark == true)
        }
        card.frontImagePath?.let { path ->
            return HomeCard(card, File(path), logoOnDark = false, isPhoto = true)
        }
        return HomeCard(card, logoModel = null, logoOnDark = false)
    }

    private fun filter(cards: List<Card>, q: String): List<Card> {
        if (q.isBlank()) return cards
        val needle = q.searchNormalized()
        return cards.filter { it.name.searchNormalized().contains(needle) }
    }

    private fun sort(cards: List<Card>, mode: SortMode): List<Card> {
        val byName = compareBy(collator) { card: Card -> card.name }
        return when (mode) {
            SortMode.RECENCY -> {
                val now = System.currentTimeMillis()
                cards.sortedWith(
                    compareByDescending<Card> { RecencyScore.effective(it, now) }
                        .thenByDescending { it.lastUsedAt ?: 0L }
                        .then(byName),
                )
            }
            SortMode.NAME_ASC -> cards.sortedWith(byName)
            SortMode.NAME_DESC -> cards.sortedWith(byName.reversed())
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { settings.setSortMode(mode) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FidCardsApplication
                HomeViewModel(
                    app.container.cardRepository,
                    app.container.settingsRepository,
                    app.container.brandCatalog,
                )
            }
        }
    }
}
