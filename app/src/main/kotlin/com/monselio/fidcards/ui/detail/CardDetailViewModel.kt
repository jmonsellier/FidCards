package com.monselio.fidcards.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.brands.Brand
import com.monselio.fidcards.brands.BrandCatalog
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une carte peut ne jamais arriver : supprimée depuis un raccourci encore posé. */
sealed interface CardDetailState {
    data object Loading : CardDetailState
    data object Missing : CardDetailState
    data class Loaded(val card: Card) : CardDetailState
}

class CardDetailViewModel(
    repository: CardRepository,
    catalog: BrandCatalog,
    settings: SettingsRepository,
    cardId: Long,
) : ViewModel() {

    /** Réglage « agrandissement automatique » ; null tant qu'il n'est pas lu. */
    val autoZoom: StateFlow<Boolean?> = settings.autoZoom
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<CardDetailState> = repository.observeById(cardId)
        .map<Card?, CardDetailState> { card ->
            if (card == null) CardDetailState.Missing else CardDetailState.Loaded(card)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDetailState.Loading)

    private val card: StateFlow<Card?> = state
        .map { (it as? CardDetailState.Loaded)?.card }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Enseigne du catalogue (couleur officielle, logo), nulle hors catalogue.
     *  Repli par nom pour les cartes créées avant l'entrée de l'enseigne. */
    val brand: StateFlow<Brand?> = card
        .map { c -> catalog.byId(c?.brandId) ?: c?.let { catalog.byName(it.name) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Uri d'asset du logo de l'enseigne, pour le coin de la carte. */
    val brandLogoUri: StateFlow<String?> = brand
        .map { catalog.logoAssetUri(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Score de récence + lastUsedAt + useCount, sous anti-rebond d'une minute :
        // rouvrir la carte ou revenir du verso ne compte pas deux fois (§4.1).
        viewModelScope.launch { repository.recordUsage(cardId) }
    }

    companion object {
        fun factory(cardId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FidCardsApplication
                CardDetailViewModel(
                    repository = app.container.cardRepository,
                    catalog = app.container.brandCatalog,
                    settings = app.container.settingsRepository,
                    cardId = cardId,
                )
            }
        }
    }
}
