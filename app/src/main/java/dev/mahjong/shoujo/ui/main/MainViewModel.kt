package dev.mahjong.shoujo.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mahjong.shoujo.cv.api.TileRecognitionEngine
import dev.mahjong.shoujo.cv.api.model.CaptureType
import dev.mahjong.shoujo.cv.api.model.RecognitionOutcome
import dev.mahjong.shoujo.image.UriImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    // Injected as the interface — never as BaselineAdapter directly
    private val recognitionEngine: TileRecognitionEngine,
    private val uriImageLoader: UriImageLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val modelIsReady: Boolean get() = recognitionEngine.isReady()
    val modelInfo get() = recognitionEngine.modelInfo

    fun onScreenshotSelected(uri: Uri) = loadAndRecognize(uri, CaptureType.SCREENSHOT)

    fun onGalleryImageSelected(uri: Uri) = loadAndRecognize(uri, CaptureType.GALLERY_PHOTO)

    /** Phase 0: skip CV entirely, go straight to manual tile entry. */
    fun onStartManualEntry() {
        _uiState.update { MainUiState.ManualEntry }
    }

    /**
     * Loads image bytes from [uri] on the IO dispatcher, then runs recognition.
     *
     * Loading and recognition are separated so each runs on the appropriate dispatcher
     * (IO for reading, Default for inference inside BaselineAdapter).
     */
    private fun loadAndRecognize(uri: Uri, captureType: CaptureType) {
        viewModelScope.launch {
            _uiState.update { MainUiState.Recognizing }

            val loadResult = withContext(Dispatchers.IO) {
                runCatching { uriImageLoader.load(uri, captureType) }
            }

            loadResult.onFailure { e ->
                _uiState.update { MainUiState.Error("Failed to load image: ${e.message}") }
                return@launch
            }

            when (val outcome = recognitionEngine.recognize(loadResult.getOrThrow())) {
                is RecognitionOutcome.Success ->
                    _uiState.update { MainUiState.RecognitionComplete(outcome.result) }
                is RecognitionOutcome.Failure ->
                    _uiState.update { MainUiState.Error(outcome.describe()) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionEngine.release()
    }
}

sealed class MainUiState {
    object Idle                        : MainUiState()
    object Recognizing                 : MainUiState()
    object ManualEntry                 : MainUiState()
    data class RecognitionComplete(
        val result: dev.mahjong.shoujo.cv.api.model.TileRecognitionResult,
    ) : MainUiState()
    data class Error(val message: String) : MainUiState()
}

private fun RecognitionOutcome.Failure.describe(): String = when (this) {
    is RecognitionOutcome.Failure.ModelNotReady -> "Model not ready: $reason"
    is RecognitionOutcome.Failure.InputError    -> "Bad input: $reason"
    is RecognitionOutcome.Failure.InferenceError -> "Inference failed: ${cause.message}"
}
