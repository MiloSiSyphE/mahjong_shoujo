package dev.mahjong.shoujo.ui.correction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mahjong.shoujo.correction.CorrectionLogger
import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.TileRecognitionResult
import dev.mahjong.shoujo.domain.ir.RecognizedHand
import dev.mahjong.shoujo.domain.ir.TileSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CorrectionViewModel @Inject constructor(
    private val correctionLogger: CorrectionLogger,
    // TODO(Phase 1): inject a shared state holder for RecognitionResult
    // (e.g., a SavedStateHandle or a Hilt-scoped RecognitionResultHolder)
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorrectionUiState())
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    // Called from MainViewModel or a shared holder after recognition completes
    fun loadResult(result: TileRecognitionResult) {
        val slots = result.tiles.mapIndexed { index, detected ->
            TileSlot(
                index           = index,
                modelSuggestion = detected.topTileId.toDomainTile(),
                modelConfidence = detected.candidates.firstOrNull()?.confidence,
                confirmedTile   = null, // user must confirm
            )
        }
        _uiState.update { it.copy(
            slots              = slots,
            sourceResult       = result,
        ) }
    }

    fun onSlotTapped(index: Int) {
        _uiState.update { it.copy(editingSlotIndex = index) }
    }

    fun onDismissEditor() {
        _uiState.update { it.copy(editingSlotIndex = null) }
    }

    fun onTileSelected(slotIndex: Int, tileId: TileId) {
        _uiState.update { state ->
            val currentSlot = state.slots[slotIndex]
            val domainTile  = tileId.toDomainTile()
            val updatedSlot = currentSlot.copy(
                confirmedTile = domainTile,
                wasCorrected  = domainTile != currentSlot.modelSuggestion,
            )
            state.copy(
                slots            = state.slots.toMutableList().also { it[slotIndex] = updatedSlot },
                editingSlotIndex = null,
            )
        }

        // Phase 2: log the correction
        viewModelScope.launch {
            val state = uiState.value
            val sourceResult = state.sourceResult ?: return@launch
            correctionLogger.log(
                recognitionResult = sourceResult,
                detectedTile      = sourceResult.tiles.getOrNull(slotIndex),
                correctedTileId   = tileId,
                imageHash         = "TODO(Phase 2): compute hash",   // TODO(Phase 2)
                imagePath         = null,                             // TODO(Phase 2)
            )
        }
    }

    fun onConfirmAll() {
        // Accept all model suggestions that haven't been manually corrected
        _uiState.update { state ->
            val slots = state.slots.map { slot ->
                if (slot.confirmedTile == null) {
                    slot.copy(confirmedTile = slot.modelSuggestion)
                } else slot
            }
            state.copy(slots = slots, isComplete = slots.all { it.isConfirmed })
        }
    }
}

data class CorrectionUiState(
    val slots: List<TileSlot> = emptyList(),
    val editingSlotIndex: Int? = null,
    val isComplete: Boolean = false,
    val sourceResult: TileRecognitionResult? = null,
) {
    val allSlotsConfirmed: Boolean
        get() = slots.isNotEmpty() && slots.all { it.isConfirmed }
}

// ---------------------------------------------------------------------------
// Mapping helpers — app layer bridges TileId (CV) ↔ Tile (domain)
// These live here because :app is the only module that sees both
// ---------------------------------------------------------------------------

private fun TileId.toDomainTile(): dev.mahjong.shoujo.domain.model.Tile? = when (this) {
    TileId.MAN_1 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 1)
    TileId.MAN_2 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 2)
    TileId.MAN_3 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 3)
    TileId.MAN_4 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 4)
    TileId.MAN_5 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 5)
    TileId.MAN_6 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 6)
    TileId.MAN_7 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 7)
    TileId.MAN_8 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 8)
    TileId.MAN_9 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.MAN, 9)
    TileId.PIN_1 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 1)
    TileId.PIN_2 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 2)
    TileId.PIN_3 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 3)
    TileId.PIN_4 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 4)
    TileId.PIN_5 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 5)
    TileId.PIN_6 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 6)
    TileId.PIN_7 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 7)
    TileId.PIN_8 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 8)
    TileId.PIN_9 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.PIN, 9)
    TileId.SOU_1 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 1)
    TileId.SOU_2 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 2)
    TileId.SOU_3 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 3)
    TileId.SOU_4 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 4)
    TileId.SOU_5 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 5)
    TileId.SOU_6 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 6)
    TileId.SOU_7 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 7)
    TileId.SOU_8 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 8)
    TileId.SOU_9 -> dev.mahjong.shoujo.domain.model.Tile.NumberTile(dev.mahjong.shoujo.domain.model.NumberSuit.SOU, 9)
    TileId.WIND_EAST  -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.EAST)
    TileId.WIND_SOUTH -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.SOUTH)
    TileId.WIND_WEST  -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.WEST)
    TileId.WIND_NORTH -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.NORTH)
    TileId.DRAGON_HAKU  -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.HAKU)
    TileId.DRAGON_HATSU -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.HATSU)
    TileId.DRAGON_CHUN  -> dev.mahjong.shoujo.domain.model.Tile.HonorTile(dev.mahjong.shoujo.domain.model.Honor.CHUN)
    TileId.UNKNOWN -> null
}
