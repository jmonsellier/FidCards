package com.monselio.fidcards.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardDetailViewModel(
    repository: CardRepository,
    cardId: Long,
) : ViewModel() {

    val card: StateFlow<Card?> = repository.observeById(cardId)
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
                CardDetailViewModel(app.container.cardRepository, cardId)
            }
        }
    }
}
