package dev.mahjong.shoujo.ui.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mahjong.shoujo.domain.engine.InvalidHandException
import dev.mahjong.shoujo.domain.engine.ScoringEngine
import dev.mahjong.shoujo.domain.model.RoundContext
import dev.mahjong.shoujo.domain.model.Wind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoundContextViewModel @Inject constructor(
    private val scoringEngine: ScoringEngine,
    // TODO: inject shared state holder for the confirmed RecognizedHand
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoundContextUiState())
    val uiState: StateFlow<RoundContextUiState> = _uiState.asStateFlow()

    fun onRoundWindChanged(wind: Wind)   = _uiState.update { it.copy(roundWind = wind).revalidate() }
    fun onSeatWindChanged(wind: Wind)    = _uiState.update { it.copy(seatWind = wind).revalidate() }
    fun onIsTsumoChanged(tsumo: Boolean) = _uiState.update { it.copy(isTsumo = tsumo).revalidate() }
    fun onHonbaChanged(v: Int)           = _uiState.update { it.copy(honbaCount = v).revalidate() }
    fun onRiichiSticksChanged(v: Int)    = _uiState.update { it.copy(riichiSticks = v).revalidate() }
    fun onRiichiChanged(v: Boolean)      = _uiState.update { it.copy(isRiichi = v, isDoubleRiichi = if (!v) false else it.isDoubleRiichi, isIppatsu = if (!v) false else it.isIppatsu).revalidate() }
    fun onDoubleRiichiChanged(v: Boolean)= _uiState.update { it.copy(isDoubleRiichi = v, isRiichi = if (v) true else it.isRiichi).revalidate() }
    fun onIppatsuChanged(v: Boolean)     = _uiState.update { it.copy(isIppatsu = v, isRiichi = if (v) true else it.isRiichi).revalidate() }
    fun onHaiteiChanged(v: Boolean)      = _uiState.update { it.copy(isHaitei = v, isHoutei = if (v) false else it.isHoutei).revalidate() }
    fun onHouteiChanged(v: Boolean)      = _uiState.update { it.copy(isHoutei = v, isHaitei = if (v) false else it.isHaitei).revalidate() }
    fun onRinshanChanged(v: Boolean)     = _uiState.update { it.copy(isRinshan = v, isChankan = if (v) false else it.isChankan).revalidate() }
    fun onChankanChanged(v: Boolean)     = _uiState.update { it.copy(isChankan = v, isRinshan = if (v) false else it.isRinshan).revalidate() }

    fun onSubmit() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.validationError != null) return@launch
            // TODO(Phase 0): retrieve Hand from shared state holder, call scoringEngine.score()
            // store ScoringResult in a shared holder, then set readyToScore = true
            _uiState.update { it.copy(readyToScore = true) }
        }
    }

    private fun RoundContextUiState.revalidate(): RoundContextUiState {
        val error = buildValidationError()
        return copy(validationError = error, readyToScore = false)
    }

    private fun RoundContextUiState.buildValidationError(): String? {
        if (isIppatsu && !isRiichi) return "Ippatsu requires Riichi"
        if (isDoubleRiichi && !isRiichi) return "Double Riichi implies Riichi"
        if (isHaitei && isHoutei) return "Haitei and Houtei are mutually exclusive"
        if (isRinshan && isChankan) return "Rinshan and Chankan are mutually exclusive"
        return null
    }
}

data class RoundContextUiState(
    val roundWind:    Wind    = Wind.EAST,
    val seatWind:     Wind    = Wind.EAST,
    val isTsumo:      Boolean = false,
    val honbaCount:   Int     = 0,
    val riichiSticks: Int     = 0,
    val isRiichi:       Boolean = false,
    val isDoubleRiichi: Boolean = false,
    val isIppatsu:      Boolean = false,
    val isHaitei:   Boolean = false,
    val isHoutei:   Boolean = false,
    val isRinshan:  Boolean = false,
    val isChankan:  Boolean = false,
    val validationError: String? = null,
    val readyToScore:    Boolean = false,
) {
    fun toRoundContext() = RoundContext(
        roundWind              = roundWind,
        seatWind               = seatWind,
        honbaCount             = honbaCount,
        riichiSticksOnTable    = riichiSticks,
        doraIndicators         = emptyList(), // TODO: populate from dora picker
        uraDoraIndicators      = emptyList(), // TODO: populate from ura picker
        isRiichi               = isRiichi,
        isDoubleRiichi         = isDoubleRiichi,
        isIppatsu              = isIppatsu,
        isHaitei               = isHaitei,
        isHoutei               = isHoutei,
        isRinshan              = isRinshan,
        isChankan              = isChankan,
    )
}
