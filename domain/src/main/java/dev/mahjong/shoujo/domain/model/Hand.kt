package dev.mahjong.shoujo.domain.model

/**
 * A complete, validated hand ready for scoring.
 *
 * This type is produced after:
 *   1. CV recognition + IR mapping
 *   2. Manual correction
 *   3. Winning tile and meld structure confirmation
 *
 * The scoring engine depends only on Hand + RoundContext — no CV details leak here.
 */
data class Hand(
    /**
     * The closed portion of the hand, NOT including the winning tile.
     * For a standard tenpai hand: 13 tiles minus any open meld tiles.
     */
    val closedTiles: List<Tile>,

    /** Open melds declared during the round (chi, pon, open kan). */
    val openMelds: List<Meld> = emptyList(),

    /** The tile that completed the hand. Always present at scoring time. */
    val winningTile: Tile,

    /** True if the winning tile was drawn from the wall (tsumo). */
    val isTsumo: Boolean,
) {
    /** Total tile count including open melds and winning tile. Should equal 14. */
    val totalTileCount: Int
        get() = closedTiles.size + openMelds.sumOf { it.tiles.size } + 1

    /** True if no melds are open (all tiles in closed hand). */
    val isClosed: Boolean get() = openMelds.isEmpty()
}

data class Meld(
    val type: MeldType,
    /** The three (or four for kan) tiles that form this meld. */
    val tiles: List<Tile>,
    /** The tile that was taken from another player's discard. Null for closed kan. */
    val calledTile: Tile?,
    /** Which player's discard was called: 0=right (kamicha), 1=across (toimen), 2=left (shimocha). */
    val calledFrom: Int?,
) {
    val isOpen: Boolean get() = calledTile != null
}

enum class MeldType { CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN }
