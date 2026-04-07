package dev.mahjong.shoujo.cv.api

import dev.mahjong.shoujo.cv.api.model.ModelInfo
import dev.mahjong.shoujo.cv.api.model.RecognitionInput
import dev.mahjong.shoujo.cv.api.model.RecognitionOutcome

/**
 * The central CV abstraction.
 *
 * ALL app and domain code that needs tile recognition depends on this interface.
 * No concrete model class, TFLite API, or baseline-specific type should ever
 * appear outside the :cv:baseline module.
 *
 * Implementations:
 *   - BaselineTileRecognitionEngine  ← wraps mahjong-utils-app detector (Phase 1)
 *   - RealPhotoTileRecognitionEngine ← fine-tuned model (Phase 3+)
 *   - ManualTileRecognitionEngine    ← returns empty result, for Phase 0 manual-only flow
 */
interface TileRecognitionEngine {
    /** Describes the underlying model. Shown in the UI and logged with corrections. */
    val modelInfo: ModelInfo

    /**
     * Runs inference on the provided input.
     * Must be called from a coroutine — implementations may dispatch to Dispatchers.Default.
     * Must NOT throw; all errors must be expressed as RecognitionOutcome.Failure.
     */
    suspend fun recognize(input: RecognitionInput): RecognitionOutcome

    /**
     * Returns true when the model is loaded and ready for inference.
     * The UI should show a loading state while this is false.
     */
    fun isReady(): Boolean

    /**
     * Release any native resources held by the model.
     * Must be idempotent.
     */
    fun release()
}
