package dev.mahjong.shoujo.cv.api.model

/**
 * Describes the model that produced a recognition result.
 * Stored verbatim in every CorrectionRecord so we can later bucket training data by source model.
 */
data class ModelInfo(
    /** Stable identifier that never changes for a given model artifact. */
    val modelId: String,
    /** Semantic version of the model artifact (e.g. "1.0.0-baseline"). */
    val version: String,
    /** Architecture family, e.g. "yolov5", "yolov8", "efficientdet". */
    val architecture: String,
    /** Domain the model was trained on — determines expected input characteristics. */
    val trainingDomain: TrainingDomain,
    /** Input image size the model expects [width, height]. */
    val expectedInputSize: Pair<Int, Int>,
)

enum class TrainingDomain {
    /** Trained on Majsoul / digital mahjong screenshots — the baseline. */
    SCREENSHOT,
    /** Trained on real photographs of physical mahjong tiles — the target domain. */
    REAL_PHOTO,
    /** Mixed or fine-tuned on both. */
    MIXED,
}
