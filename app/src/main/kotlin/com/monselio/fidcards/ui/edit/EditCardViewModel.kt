package com.monselio.fidcards.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.barcode.BarcodeEncoder
import com.monselio.fidcards.brands.Brand
import com.monselio.fidcards.brands.BrandCatalog
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val barcodeValue: String = "",
    val displayValue: String = "",
    val format: BarcodeFormat = BarcodeFormat.EAN_13,
    val brand: Brand? = null,
    /** Enseignes partageant la même carte, à signaler pour éviter les doublons (§6.4). */
    val groupSiblings: List<Brand> = emptyList(),
    val colorArgb: Int? = null,
    val suggestions: List<Brand> = emptyList(),
    val nameError: Boolean = false,
    /** La valeur ne s'encode pas dans le format choisi (checksum, caractères, longueur). */
    val valueError: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

class EditCardViewModel(
    private val repository: CardRepository,
    private val catalog: BrandCatalog,
    private val cardId: Long?,
    scannedValue: String?,
    scannedFormat: BarcodeFormat?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditUiState(
            loaded = cardId == null,
            isNew = cardId == null,
            barcodeValue = scannedValue.orEmpty(),
            format = scannedFormat ?: BarcodeFormat.EAN_13,
        ),
    )
    val uiState: StateFlow<EditUiState> = _uiState

    private var editedCard: Card? = null

    init {
        if (cardId != null) {
            viewModelScope.launch {
                val card = repository.getById(cardId)
                if (card == null) {
                    _uiState.update { it.copy(loaded = true) }
                } else {
                    editedCard = card
                    val (brand, siblings) = withContext(Dispatchers.IO) {
                        val b = catalog.byId(card.brandId)
                        b to b?.let(catalog::groupSiblings).orEmpty()
                    }
                    _uiState.update {
                        it.copy(
                            loaded = true,
                            isNew = false,
                            name = card.name,
                            barcodeValue = card.barcodeValue,
                            displayValue = card.displayValue.orEmpty(),
                            format = card.barcodeFormat,
                            brand = brand,
                            groupSiblings = siblings,
                            colorArgb = card.colorArgb,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { state ->
            state.copy(
                name = value,
                nameError = false,
                // Modifier le nom à la main détache l'enseigne sélectionnée.
                brand = state.brand?.takeIf { it.name == value },
                groupSiblings = if (state.brand?.name == value) state.groupSiblings else emptyList(),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val suggestions = if (value.isBlank()) emptyList() else catalog.search(value)
            _uiState.update {
                // Ne pas rouvrir la liste si l'utilisateur vient de choisir cette enseigne.
                if (it.brand?.name == value) it.copy(suggestions = emptyList())
                else it.copy(suggestions = suggestions)
            }
        }
    }

    fun selectBrand(brand: Brand) {
        viewModelScope.launch(Dispatchers.IO) {
            val siblings = catalog.groupSiblings(brand)
            _uiState.update {
                it.copy(
                    name = brand.name,
                    brand = brand,
                    groupSiblings = siblings,
                    suggestions = emptyList(),
                    nameError = false,
                )
            }
        }
    }

    fun onValueChange(value: String) {
        _uiState.update { it.copy(barcodeValue = value, valueError = false) }
    }

    fun onDisplayValueChange(value: String) {
        _uiState.update { it.copy(displayValue = value) }
    }

    fun onFormatChange(format: BarcodeFormat) {
        _uiState.update { it.copy(format = format, valueError = false) }
    }

    fun onColorSelect(argb: Int?) {
        _uiState.update { it.copy(colorArgb = argb) }
    }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        val value = state.barcodeValue.trim()
        val encodable = value.isNotEmpty() && runCatching {
            BarcodeEncoder.encode(value, state.format)
        }.isSuccess
        if (name.isEmpty() || !encodable) {
            _uiState.update { it.copy(nameError = name.isEmpty(), valueError = !encodable) }
            return
        }
        viewModelScope.launch {
            val existing = editedCard
            if (existing == null) {
                repository.insert(
                    Card(
                        name = name,
                        barcodeValue = value,
                        displayValue = state.displayValue.trim().takeIf { it.isNotEmpty() },
                        barcodeFormat = state.format,
                        brandId = state.brand?.id,
                        colorArgb = state.colorArgb,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                repository.update(
                    existing.copy(
                        name = name,
                        barcodeValue = value,
                        displayValue = state.displayValue.trim().takeIf { it.isNotEmpty() },
                        barcodeFormat = state.format,
                        brandId = state.brand?.id,
                        colorArgb = state.colorArgb,
                    ),
                )
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val card = editedCard ?: return
        viewModelScope.launch {
            repository.delete(card)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    companion object {
        fun factory(cardId: Long?, scannedValue: String?, scannedFormat: String?) =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as FidCardsApplication
                    EditCardViewModel(
                        repository = app.container.cardRepository,
                        catalog = app.container.brandCatalog,
                        cardId = cardId,
                        scannedValue = scannedValue,
                        scannedFormat = scannedFormat?.let {
                            runCatching { BarcodeFormat.valueOf(it) }.getOrNull()
                        },
                    )
                }
            }
    }
}
