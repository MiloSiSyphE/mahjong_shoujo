package dev.mahjong.shoujo.cv.api.model

import dev.mahjong.shoujo.cv.api.TileId

/**
 * A single detection from the CV pipeline.
 *
 * This is part of the Stable Intermediate Representation (IR).
 * It must survive model replacement — every field here should have a
 * well-defined meaning regardless of which model produced it.
 */
data class DetectedTile(
    /**
     * Ordered list of tile candidates for this detection, highest confidence first.
     * The adapter MUST always include at least one candidate.
     * For a high-confidence detection this list will typically have one entry.
     * For ambiguous detections include the top-k alternatives so the correction UI
     * can offer meaningful choices.
     */
    val candidates: List<RecognitionCandidate>,

    /**
     * Spatial location of the tile in the input image, normalised to [0,1].
     * Null if the model does not produce bounding boxes (e.g., a pure classifier).
     */
    val bbox: NormalizedBbox?,

    /**
     * Hint about which logical group this tile belongs to within the hand.
     * E.g., closed tiles vs. open meld index.
     * May be null if the model does not segment the hand into groups.
     */
    val groupHint: GroupHint?,

    /**
     * Estimated order/index of this tile within its group, left-to-right.
     * Null if ordering cannot be determined.
     */
    val positionIndex: Int?,
) {
    /** Convenience: the top candidate TileId, or UNKNOWN if candidates is empty. */
    val topTileId: TileId get() = candidates.firstOrNull()?.tileId ?: TileId.UNKNOWN

    /** Convenience: whether confidence for the top candidate is below the warning threshold. */
    val isLowConfidence: Boolean
        get() = (candidates.firstOrNull()?.confidence ?: 0f) < LOW_CONFIDENCE_THRESHOLD

    companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 0.70f
    }
}

data class RecognitionCandidate(
    val tileId: TileId,
    /** Normalised probability in [0, 1]. */
    val confidence: Float,
)

/**
 * Bounding box with coordinates normalised to [0,1] relative to the input image dimensions.
 * (0,0) is top-left.
 */
data class NormalizedBbox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width:  Float get() = right - left
    val height: Float get() = bottom - top
    val cx:     Float get() = left + width / 2f
    val cy:     Float get() = top + height / 2f
}

enum class GroupHint {
    CLOSED_HAND,
    OPEN_MELD_0,
    OPEN_MELD_1,
    OPEN_MELD_2,
    OPEN_MELD_3,
    UNKNOWN,
}
