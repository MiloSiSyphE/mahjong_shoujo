package dev.mahjong.shoujo.domain.model

/**
 * The canonical domain representation of a single mahjong tile.
 *
 * This type is INDEPENDENT of the CV pipeline. It is what the scoring engine works with.
 * The mapping from cv.api.TileId → Tile lives in the app layer (IrMapper).
 */
sealed class Tile {
    data class NumberTile(
        val suit: NumberSuit,
        val number: Int,
        val isAkadora: Boolean = false,
    ) : Tile() {
        init { require(number in 1..9) { "Tile number must be 1..9, got $number" } }
    }
    data class HonorTile(val honor: Honor) : Tile()

    val isTerminal: Boolean get() = this is NumberTile && (number == 1 || number == 9)
    val isHonor:    Boolean get() = this is HonorTile
    val isSimple:   Boolean get() = this is NumberTile && number in 2..8

    override fun toString(): String = when (this) {
        is NumberTile -> "${number}${suit.symbol}"
        is HonorTile  -> honor.name
    }
}

enum class NumberSuit(val symbol: Char) {
    MAN('m'), PIN('p'), SOU('s')
}

enum class Honor {
    EAST, SOUTH, WEST, NORTH,
    HAKU, HATSU, CHUN;

    val isWind:   Boolean get() = ordinal < 4
    val isDragon: Boolean get() = ordinal >= 4
}

enum class Wind { EAST, SOUTH, WEST, NORTH }
