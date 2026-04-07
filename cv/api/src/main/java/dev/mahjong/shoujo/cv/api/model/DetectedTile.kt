package dev.mahjong.shoujo.cv.api.model

import dev.mahjong.shoujo.cv.api.TileId

/**
 * A single detection from the CV pipeline.
 *
 * This is part of the Stable Intermediate Representation (IR).
 * It must survive model replacement — every field here should have a
 * well-defined meaning regardless of which model produced it.
 *
 * Nullable fields mean "this model did not provide this information."
 * Downstream code must degrade gracefully to user confirmation for any null field.
 */
data class DetectedTile(
    // ── Classification ────────────────────────────────────────────────
    /**
     * Ordered list of tile candidates for this detection, highest confidence first.
     * The adapter MUST always include at least one candidate.
     * For a high-confidence detection this list will typically have one entry.
     * For ambiguous detections include the top-k alternatives so the correction UI
     * can offer meaningful choices.
     */
    val candidates: List<RecognitionCandidate>,

    /** Whether the model flagged this as an akadora (red five). */
    val isAkadora: Boolean = false,

    // ── Geometry ──────────────────────────────────────────────────────
    /**
     * Spatial location of the tile in the input image, normalised to [0,1].
     * Null if the model does not produce bounding boxes (e.g., a pure classifier).
     */
    val bbox: NormalizedBbox?,

    /**
     * Clockwise rotation of the tile in degrees (0, 90, 180, 270).
     * Null if model does not predict orientation.
     * Primarily useful for real-photo models where tiles may be rotated.
     */
    val orientationDegrees: Int? = null,

    // ── Layout semantics ──────────────────────────────────────────────
    /**
     * Structural role of this tile within the hand layout.
     * Null if the model does not segment the hand.
     */
    val layoutRole: LayoutRole? = null,

    /**
     * Opaque group identifier. Tiles with the same non-null groupId are
     * hypothesised by the model to belong to the same meld or hand segment.
     * Group semantics (meld type etc.) are determined in the correction stage.
     */
    val groupId: String? = null,

    /**
     * Suggested display/reading order index within its group, 0-based.
     * Null if ordering cannot be inferred.
     */
    val readingOrder: Int? = null,

    // ── Uncertainty ───────────────────────────────────────────────────
    /**
     * Overall detection confidence (from the detector head, distinct from
     * classification confidence). Null for models without a detection head.
     */
    val detectionConfidence: Float? = null,

    // ── Debug ─────────────────────────────────────────────────────────
    /**
     * Adapter-specific key/value metadata (e.g. raw YOLO objectness score).
     * Never used for decisions; for logging and diagnostics only.
     */
    val debugMetadata: Map<String, String> = emptyMap(),
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

/**
 * Structural role of a tile within the hand layout.
 * Replaces the old GroupHint enum — more expressive and not limited to four melds.
 */
enum class LayoutRole {
    /** Part of the concealed tiles dealt to the player. */
    CLOSED_HAND,
    /** The winning tile (tsumo or ron). */
    WINNING_TILE,
    /** A tile in an open meld (chi/pon/kan). */
    OPEN_MELD,
    /** A tile in a declared kan (open or concealed). */
    KAN,
    /** Role not yet assigned or model did not provide a hint. */
    UNKNOWN,
}
