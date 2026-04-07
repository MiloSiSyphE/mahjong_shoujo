package dev.mahjong.shoujo.ui.result

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mahjong.shoujo.domain.model.ScoringResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ScoringResultViewModel @Inject constructor(
    // TODO(Phase 0): inject shared ScoringResultHolder (Hilt singleton)
    // and observe its result here
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScoringResultUiState>(ScoringResultUiState.Loading)
    val uiState: StateFlow<ScoringResultUiState> = _uiState.asStateFlow()

    // TODO(Phase 0): collect from shared result holder instead of using a stub
    init {
        _uiState.value = ScoringResultUiState.Error("ScoringResultViewModel not yet wired (Phase 0 TODO)")
    }
}

sealed class ScoringResultUiState {
    object Loading : ScoringResultUiState()
    data class Ready(val result: ScoringResult) : ScoringResultUiState()
    data class Error(val message: String) : ScoringResultUiState()
}
