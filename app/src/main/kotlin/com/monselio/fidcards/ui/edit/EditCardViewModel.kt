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
import com.monselio.fidcards.data.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CardImageKind { LOGO, FRONT, BACK }

data class EditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val barcodeValue: String = "",
    val displayValue: String = "",
    val format: BarcodeFormat = BarcodeFormat.EAN_13,
    val brand: Brand? = null,
    val colorArgb: Int? = null,
    /** Logo choisi à la main (fichier local), prioritaire sur celui du catalogue. */
    val logoPath: String? = null,
    val frontPath: String? = null,
    val backPath: String? = null,
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
    private val imageStore: ImageStore,
    private val cardId: Long?,
    scannedValue: String?,
    scannedFormat: BarcodeFormat?,
) : ViewModel() {

    /** Fichiers importés pendant l'édition, à purger s'ils ne sont pas enregistrés. */
    private val pickedImages = mutableListOf<String>()
    private var savedImagePaths: Set<String> = emptySet()

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
                    val brand = withContext(Dispatchers.IO) { catalog.byId(card.brandId) }
                    _uiState.update {
                        it.copy(
                            loaded = true,
                            isNew = false,
                            name = card.name,
                            barcodeValue = card.barcodeValue,
                            displayValue = card.displayValue.orEmpty(),
                            format = card.barcodeFormat,
                            brand = brand,
                            colorArgb = card.colorArgb,
                            logoPath = card.logoPath,
                            frontPath = card.frontImagePath,
                            backPath = card.backImagePath,
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
        _uiState.update {
            it.copy(
                name = brand.name,
                brand = brand,
                suggestions = emptyList(),
                nameError = false,
            )
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

    fun onImagePicked(kind: CardImageKind, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = imageStore.import(uri) ?: return@launch
            pickedImages += path
            _uiState.update {
                when (kind) {
                    CardImageKind.LOGO -> it.copy(logoPath = path)
                    CardImageKind.FRONT -> it.copy(frontPath = path)
                    CardImageKind.BACK -> it.copy(backPath = path)
                }
            }
        }
    }

    fun removeImage(kind: CardImageKind) {
        _uiState.update {
            when (kind) {
                CardImageKind.LOGO -> it.copy(logoPath = null)
                CardImageKind.FRONT -> it.copy(frontPath = null)
                CardImageKind.BACK -> it.copy(backPath = null)
            }
        }
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
                        logoPath = state.logoPath,
                        frontImagePath = state.frontPath,
                        backImagePath = state.backPath,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                // Les images remplacées ou retirées sont supprimées du disque.
                listOf(
                    existing.logoPath to state.logoPath,
                    existing.frontImagePath to state.frontPath,
                    existing.backImagePath to state.backPath,
                ).forEach { (old, new) -> if (old != new) imageStore.delete(old) }
                repository.update(
                    existing.copy(
                        name = name,
                        barcodeValue = value,
                        displayValue = state.displayValue.trim().takeIf { it.isNotEmpty() },
                        barcodeFormat = state.format,
                        brandId = state.brand?.id,
                        colorArgb = state.colorArgb,
                        logoPath = state.logoPath,
                        frontImagePath = state.frontPath,
                        backImagePath = state.backPath,
                    ),
                )
            }
            savedImagePaths = setOfNotNull(state.logoPath, state.frontPath, state.backPath)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val card = editedCard ?: return
        viewModelScope.launch {
            repository.delete(card)
            imageStore.delete(card.logoPath)
            imageStore.delete(card.frontImagePath)
            imageStore.delete(card.backImagePath)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    override fun onCleared() {
        // Purge des images importées puis abandonnées (édition annulée ou remplacées).
        pickedImages.filterNot { it in savedImagePaths }.forEach(imageStore::delete)
    }

    companion object {
        fun factory(cardId: Long?, scannedValue: String?, scannedFormat: String?) =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as FidCardsApplication
                    EditCardViewModel(
                        repository = app.container.cardRepository,
                        catalog = app.container.brandCatalog,
                        imageStore = app.container.imageStore,
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
