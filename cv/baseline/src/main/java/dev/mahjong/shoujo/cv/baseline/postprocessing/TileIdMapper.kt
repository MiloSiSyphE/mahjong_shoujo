package dev.mahjong.shoujo.cv.baseline.postprocessing

import dev.mahjong.shoujo.cv.api.TileId

/**
 * Maps baseline model class indices (0-based integers) to canonical [TileId] values
 * and an akadora flag.
 *
 * BASELINE-SPECIFIC: The index-to-tile mapping is a property of how the baseline
 * model was trained. A new model trained with a different class ordering would
 * need a different mapper, NOT changes to TileId.
 *
 * TODO(Phase 1): populate BASELINE_CLASS_MAP by inspecting the mahjong-utils-app
 *               label file / class list shipped with the .tflite asset.
 *               Confirm the order matches the model's output tensor class dimension.
 *               Include akadora entries (e.g. index 37 = red 5-man → MAN_5, true).
 *
 * DESIGN NOTE: This mapping is the ONLY place where baseline class indices appear.
 * Keeping it isolated here means swapping the model requires only updating this map
 * (and possibly BaselineConfig.NUM_CLASSES).
 */
class TileIdMapper {

    /**
     * Maps a model class index to a [TileId] and akadora flag.
     * Returns [TileId.UNKNOWN] with isAkadora=false for out-of-range or unmapped indices.
     */
    fun fromClassIndex(index: Int): Pair<TileId, Boolean> =
        BASELINE_CLASS_MAP.getOrElse(index) { Pair(TileId.UNKNOWN, false) }

    companion object {
        /**
         * TODO(Phase 1): fill in the correct mapping from the baseline model's label list.
         * The indices 0..33 should map to the 34 standard tile types (isAkadora = false).
         * Additional indices for red fives should map to the same TileId with isAkadora = true.
         * Confirm ordering (e.g., man 1-9, pin 1-9, sou 1-9, winds, dragons).
         *
         * Example shape (order and akadora indices depend on the actual model):
         *   0  -> Pair(TileId.MAN_1, false),
         *   ...
         *   4  -> Pair(TileId.MAN_5, false),
         *   ...
         *   37 -> Pair(TileId.MAN_5, true),   // red 5-man class
         */
        private val BASELINE_CLASS_MAP: Map<Int, Pair<TileId, Boolean>> = mapOf(
            // PLACEHOLDER — replace with actual mapping from mahjong-utils-app
        )
    }
}
