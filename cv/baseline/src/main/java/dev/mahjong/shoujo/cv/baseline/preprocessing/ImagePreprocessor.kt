package dev.mahjong.shoujo.cv.baseline.preprocessing

import android.graphics.Bitmap
import android.net.Uri
import android.content.Context
import dev.mahjong.shoujo.cv.baseline.BaselineConfig

/**
 * Converts raw input images into the float tensor expected by the baseline model.
 *
 * BASELINE-SPECIFIC: The normalisation formula, resize strategy, and channel order
 * are all properties of the current model training pipeline.
 * When the model changes, this class changes — nothing outside :cv:baseline
 * should be affected.
 *
 * Assumptions for the current baseline (screenshot-oriented model):
 *   - Input: RGB, 0-255 pixel values
 *   - Resize: letterbox to 640×640 to preserve aspect ratio (YOLO convention)
 *   - Normalisation: divide by 255 → [0, 1]
 *   - Channel layout: NHWC, float32
 *
 * TODO(Phase 1): verify these assumptions against mahjong-utils-app inference code.
 * TODO(Phase 3): for a real-photo model, preprocessing may need augmentation/colour-jitter
 *               awareness at inference time (e.g., histogram equalisation for dim photos).
 */
class ImagePreprocessor(private val context: Context) {

    /**
     * Loads a Bitmap from a content URI.
     * Handles sampling / downscaling for very large gallery images.
     */
    fun loadFromUri(uri: Uri): Bitmap {
        // TODO(Phase 1): implement with BitmapFactory + inSampleSize to cap memory usage
        throw NotImplementedError("loadFromUri not yet implemented")
    }

    /**
     * Resizes [bitmap] to [BaselineConfig.INPUT_WIDTH] × [BaselineConfig.INPUT_HEIGHT]
     * using letterboxing (padding with grey, not stretching).
     *
     * Returns the resized bitmap AND the letterbox metadata needed to
     * map model output coordinates back to original image space.
     */
    fun letterbox(bitmap: Bitmap): LetterboxResult {
        // TODO(Phase 1): implement letterbox resize
        throw NotImplementedError("letterbox not yet implemented")
    }

    /**
     * Converts a letterboxed [Bitmap] to a float32 ByteBuffer in NHWC layout,
     * normalised to [0, 1].
     */
    fun toModelInput(letterboxed: Bitmap): FloatArray {
        // TODO(Phase 1): implement pixel normalisation
        throw NotImplementedError("toModelInput not yet implemented")
    }
}

/**
 * Metadata describing how a source image was padded/scaled to fit the model input.
 * Required to map model-output bounding boxes back to original image coordinates.
 */
data class LetterboxResult(
    val resizedBitmap: Bitmap,
    val scaleX: Float,
    val scaleY: Float,
    val padLeft: Int,
    val padTop: Int,
)
