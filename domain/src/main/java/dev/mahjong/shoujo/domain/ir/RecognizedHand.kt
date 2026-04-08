package dev.mahjong.shoujo.domain.ir

import dev.mahjong.shoujo.domain.model.Hand
import dev.mahjong.shoujo.domain.model.Meld
import dev.mahjong.shoujo.domain.model.Tile
import dev.mahjong.shoujo.domain.model.MeldType as ScoringMeldType

/**
 * The app-layer bridge between the correction screen output and the scoring engine.
 *
 * Lives in :domain to avoid making :app aware of CV internals.
 * This is the *corrected* state — after the user has confirmed tile identities
 * and (for open hands) the meld structure.
 *
 * Structure:
 *   - [closedTiles] — tiles in the player's concealed hand, including the winning tile
 *     (identified by [TileSlot.isWinningTile]).
 *   - [openMelds] — zero or more user-confirmed meld groups. Each group carries its
 *     tile identities and, once the user selects a meld type, the [MeldType].
 *
 * Phase 2 wiring:
 *   - CorrectionViewModel builds this after "Confirm Hand"
 *   - RoundContextViewModel receives it; the user confirms meld types there
 *   - [toHand] is called with the fully-confirmed RecognizedHand just before scoring
 */
data class RecognizedHand(
    /**
     * Tiles in the concealed portion of the hand, including the winning tile.
     * At least one must have [TileSlot.isWinningTile] = true at scoring time.
     */
    val closedTiles: List<TileSlot>,

    /**
     * Open meld groups confirmed at the correction stage.
     * Empty for a fully closed hand.
     * [MeldType] within each group is null until the user confirms it on the
     * RoundContextScreen (Phase 2 TODO).
     */
    val openMelds: List<ConfirmedMeldGroup> = emptyList(),

    /**
     * True when every tile slot (closed + open meld tiles) has a confirmed identity.
     * The scoring screen must not be reachable until this is true.
     */
    val isComplete: Boolean = closedTiles.all { it.isConfirmed } &&
        openMelds.all { group -> group.tiles.all { it.isConfirmed } },
) {
    /**
     * Attempt to build a [Hand] ready for the scoring engine.
     *
     * Returns null when:
     *   - not all tile slots are confirmed ([isComplete] is false), or
     *   - no winning tile is designated, or
     *   - any open meld group has a null [MeldType] (user has not confirmed it yet).
     *
     * The scoring engine is never called with a null [Hand]; callers must gate
     * on a non-null return before proceeding to the scoring screen.
     */
    fun toHand(isTsumo: Boolean): Hand? {
        if (!isComplete) return null

        // Every open meld must have a confirmed meld type before we can score.
        // TODO(Phase 2): RoundContextScreen populates meldType via onMeldTypeSelected().
        if (openMelds.any { it.meldType == null }) return null

        val winningSlot = closedTiles.firstOrNull { it.isWinningTile } ?: return null
        val winningTile = winningSlot.confirmedTile ?: return null

        val closed = closedTiles
            .filter { !it.isWinningTile }
            .mapNotNull { it.confirmedTile }

        val melds = openMelds.map { group -> group.toMeld() ?: return null }

        return Hand(
            closedTiles = closed,
            openMelds = melds,
            winningTile = winningTile,
            isTsumo = isTsumo,
        )
    }
}

/**
 * A user-confirmed open meld group after the correction stage.
 *
 * The tile identities are confirmed at the correction screen.
 * The [meldType] and [claimedTileIndex] are confirmed at the RoundContextScreen
 * (Phase 2 TODO).
 */
data class ConfirmedMeldGroup(
    /**
     * Opaque identifier propagated from [DetectedTile.groupId].
     * Used to correlate with the original CV output for correction logging.
     */
    val groupId: String,

    /** The tiles that form this meld, in left-to-right reading order. */
    val tiles: List<TileSlot>,

    /**
     * The type of this meld (chi, pon, or kan).
     * Null until the user confirms it on the RoundContextScreen.
     * TODO(Phase 2): wire RoundContextScreen meld-type picker.
     */
    val meldType: MeldType? = null,

    /**
     * For [MeldType.CHI]: 0-based index of the tile within [tiles] that was
     * called from another player's discard. Required for correct fu calculation.
     * Null until confirmed by the user.
     * TODO(Phase 2): wire RoundContextScreen claimed-tile selector.
     */
    val claimedTileIndex: Int? = null,
) {
    /**
     * Converts this confirmed group to a scoring-engine [Meld].
     * Returns null if [meldType] is not yet set (not ready to score).
     */
    internal fun toMeld(): Meld? {
        val type = meldType ?: return null
        val meldTiles = tiles.mapNotNull { it.confirmedTile }
        val calledTile: Tile? = when (type) {
            MeldType.CHI -> {
                // Use the user-selected claimed tile index if available.
                // TODO(Phase 2): enforce non-null claimedTileIndex once the UI sets it.
                //   For now fall back to first tile so the engine is callable in testing.
                meldTiles.getOrNull(claimedTileIndex ?: 0)
            }
            MeldType.PON -> meldTiles.firstOrNull()
            MeldType.KAN ->
                // Open kan: first tile is the called tile.
                // TODO(Phase 3): distinguish open-kan / closed-kan / added-kan from user input.
                meldTiles.firstOrNull()
        }
        return Meld(
            type = type.toScoringMeldType(),
            tiles = meldTiles,
            calledTile = calledTile,
            calledFrom = null, // TODO(Phase 2): capture "called from which player" in UI
        )
    }
}

/**
 * IR-level meld type, set at the RoundContextScreen.
 *
 * Intentionally simpler than the scoring-engine [ScoringMeldType] — kan subtypes
 * (open/closed/added) are deferred to Phase 3 when the distinction matters for fu.
 *
 * Separate from [dev.mahjong.shoujo.domain.model.MeldType] to avoid leaking
 * scoring-level distinctions into the correction/context UI.
 */
enum class MeldType {
    CHI,
    PON,
    /** Open or concealed kan — subtype resolved in Phase 3. */
    KAN,
}

/** Maps IR [MeldType] → scoring-engine [ScoringMeldType]. */
private fun MeldType.toScoringMeldType(): ScoringMeldType = when (this) {
    MeldType.CHI -> ScoringMeldType.CHI
    MeldType.PON -> ScoringMeldType.PON
    // TODO(Phase 3): map to OPEN_KAN / CLOSED_KAN / ADDED_KAN based on user input.
    MeldType.KAN -> ScoringMeldType.OPEN_KAN
}

// ─────────────────────────────────────────────────────────────────────────────

data class TileSlot(
    /** 0-based position index within the hand (left-to-right). */
    val index: Int,

    /**
     * The CV model's top candidate, or null if the model produced no detection
     * for this position. Always shown to the user as the starting point.
     */
    val modelSuggestion: Tile?,

    /**
     * Confidence of [modelSuggestion]. Used to highlight low-confidence slots in the UI.
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

    /**
     * True if this slot is designated as the winning tile (tsumo draw or ron tile).
     * Only meaningful for slots in [RecognizedHand.closedTiles].
     * TODO(Phase 2): wire the winning-tile picker in CorrectionScreen.
     */
    val isWinningTile: Boolean = false,
) {
    val isConfirmed: Boolean get() = confirmedTile != null
    val isLowConfidence: Boolean get() = (modelConfidence ?: 1f) < 0.70f
}
