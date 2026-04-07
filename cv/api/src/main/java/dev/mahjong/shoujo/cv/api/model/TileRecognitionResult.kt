package dev.mahjong.shoujo.cv.api.model

/**
 * The complete, stable output of one recognition pass.
 *
 * This is the IR boundary: everything downstream (UI, correction flow, scoring)
 * depends on this type, NOT on any internal baseline-adapter types.
 *
 * When the model is replaced, the adapter produces a new TileRecognitionResult
 * using the same schema — the rest of the app sees no change.
 */
data class TileRecognitionResult(
    /** Detected tiles in the order the model produced them (typically left-to-right). */
    val tiles: List<DetectedTile>,

    /** Identity of the model that produced this result — persisted with every correction. */
    val modelInfo: ModelInfo,

    /** How the source image was captured — affects correction logging. */
    val captureType: CaptureType,

    /** Wall-clock milliseconds spent in inference + post-processing. */
    val processingTimeMs: Long,

    /**
     * Pass-through of any model-specific metadata the adapter wants to preserve
     * for debugging. Must not be used by domain or UI code for decisions.
     * Safe to ignore / log only.
     */
    val debugMetadata: Map<String, String> = emptyMap(),
)

/** Wraps the result or a typed failure reason. */
sealed class RecognitionOutcome {
    data class Success(val result: TileRecognitionResult) : RecognitionOutcome()
    sealed class Failure : RecognitionOutcome() {
        data class ModelNotReady(val reason: String) : Failure()
        data class InputError(val reason: String) : Failure()
        data class InferenceError(val cause: Throwable) : Failure()
    }
}
