package dev.mahjong.shoujo.domain.ir

import dev.mahjong.shoujo.domain.model.Hand
import dev.mahjong.shoujo.domain.model.RoundContext
import dev.mahjong.shoujo.domain.model.Tile

/**
 * The app-layer bridge between the CV output (TileRecognitionResult) and the domain (Hand).
 *
 * Lives in :domain to avoid making :app aware of CV internals.
 * Contains both the model's best guess AND a flag for each tile indicating
 * whether it was manually corrected.
 *
 * IMPORTANT: This type is the "corrected state" passed to the scoring engine,
 * NOT the raw CV output. The correction flow populates this from TileRecognitionResult
 * and user edits.
 */
data class RecognizedHand(
    /** One entry per detected tile position. May be incomplete until the user confirms. */
    val slots: List<TileSlot>,

    /**
     * True when all slots have a confirmed tile identity.
     * The scoring screen should not be reachable until this is true.
     */
    val isComplete: Boolean = slots.all { it.isConfirmed },
) {
    /**
     * Attempt to build a [Hand] from this recognized hand.
     * Returns null if not all slots are confirmed or if winningTile is unset.
     */
    fun toHand(isTsumo: Boolean): Hand? {
        if (!isComplete) return null
        val winningSlot = slots.firstOrNull { it.isWinningTile } ?: return null
        val winningTile = winningSlot.confirmedTile ?: return null
        val closedTiles = slots
            .filter { !it.isWinningTile && it.meldIndex == null }
            .mapNotNull { it.confirmedTile }
        return Hand(
            closedTiles = closedTiles,
            openMelds = emptyList(), // TODO: reconstruct open melds from meldIndex groups
            winningTile = winningTile,
            isTsumo = isTsumo,
        )
    }
}

data class TileSlot(
    /** 0-based position index within the hand (left-to-right). */
    val index: Int,

    /**
     * The CV model's top candidate, or null if the model produced no detection
     * for this position. Always shown to the user as the starting point.
     */
    val modelSuggestion: Tile?,

    /**
     * Confidence of modelSuggestion. Used to highlight low-confidence slots in the UI.
     * Null if this slot was added manually (no model candidate).
     */
    val modelConfidence: Float?,

    /**
     * The canonical tile after user confirmation or correction.
     * Null until the user has either accepted or changed the model suggestion.
     */
    val confirmedTile: Tile?,

    /** True if the user explicitly changed the model suggestion. */
    val wasCorrected: Boolean = false,

    /** True if this slot is designated as the winning tile. */
    val isWinningTile: Boolean = false,

    /**
     * If non-null, this tile belongs to the open meld at this index (0-3).
     * Null means the tile is in the closed portion of the hand.
     */
    val meldIndex: Int? = null,
) {
    val isConfirmed: Boolean get() = confirmedTile != null
    val isLowConfidence: Boolean get() = (modelConfidence ?: 1f) < 0.70f
}
