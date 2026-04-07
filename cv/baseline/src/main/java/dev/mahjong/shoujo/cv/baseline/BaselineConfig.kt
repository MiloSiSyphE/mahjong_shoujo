package dev.mahjong.shoujo.cv.baseline

import dev.mahjong.shoujo.cv.api.model.ModelInfo
import dev.mahjong.shoujo.cv.api.model.TrainingDomain

/**
 * All baseline-specific constants live here.
 *
 * THESE VALUES ARE BASELINE-SPECIFIC AND MUST NOT LEAK OUT OF :cv:baseline.
 *
 * When the baseline model is replaced:
 *   - Change the values here (or add a new Config class).
 *   - Do NOT touch any code outside :cv:baseline.
 *
 * The values below are intentionally left as TODOs because they need to be
 * reverse-engineered from the mahjong-utils-app assets before Phase 1 begins.
 */
object BaselineConfig {

    // TODO(Phase 1): confirm from mahjong-utils-app assets
    const val MODEL_ASSET_PATH = "models/baseline_detector.tflite"

    // TODO(Phase 1): confirm from mahjong-utils-app model metadata or source
    const val INPUT_WIDTH  = 640
    const val INPUT_HEIGHT = 640

    // TODO(Phase 1): set to match the model's num_classes output
    const val NUM_CLASSES = 34

    // TODO(Phase 1): tune after observing baseline recall/precision
    const val CONFIDENCE_THRESHOLD = 0.50f
    const val IOU_THRESHOLD         = 0.45f

    // TODO(Phase 1): confirm max detection count from model output shape
    const val MAX_DETECTIONS = 100

    /**
     * Whether to attempt GPU delegate.
     * Keep false until baseline model is confirmed working on CPU first.
     */
    const val USE_GPU_DELEGATE = false

    val MODEL_INFO = ModelInfo(
        modelId = "baseline-majsoul-v1",
        version = "1.0.0-baseline",
        architecture = "yolov5",           // TODO(Phase 1): confirm architecture
        trainingDomain = TrainingDomain.SCREENSHOT,
        expectedInputSize = Pair(INPUT_WIDTH, INPUT_HEIGHT),
    )
}
