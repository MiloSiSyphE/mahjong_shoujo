package dev.mahjong.shoujo.domain.model

/**
 * All situational information required for correct yaku and point calculation.
 *
 * This is collected from the user via the RoundContext input screen.
 * The scoring engine must not apply defaults silently — prefer explicit user input.
 */
data class RoundContext(
    val roundWind: Wind,
    val seatWind: Wind,

    /** Number of extra counters (honba) on the table. Affects point transfers. */
    val honbaCount: Int,

    /** Number of riichi sticks on the table. Collected by winner. */
    val riichiSticksOnTable: Int,

    // --- Dora ---
    val doraIndicators: List<Tile>,
    val uraDoraIndicators: List<Tile>,

    // --- Riichi state ---
    val isRiichi: Boolean,
    val isDoubleRiichi: Boolean,
    val isIppatsu: Boolean,

    // --- Special win conditions ---
    val isHaitei: Boolean,   // Last tile from wall (tsumo)
    val isHoutei: Boolean,   // Last tile discarded (ron)
    val isRinshan: Boolean,  // Win after kan draw
    val isChankan: Boolean,  // Win by robbing a kan
) {
    init {
        require(honbaCount >= 0) { "honbaCount must be >= 0" }
        require(riichiSticksOnTable >= 0) { "riichiSticksOnTable must be >= 0" }
        require(!(isDoubleRiichi && !isRiichi)) { "doubleRiichi implies riichi" }
        require(!(isIppatsu && !isRiichi)) { "ippatsu implies riichi" }
        require(!(isHaitei && isHoutei)) { "haitei and houtei are mutually exclusive" }
        require(!(isRinshan && isChankan)) { "rinshan and chankan are mutually exclusive" }
    }

    companion object {
        /** Minimal context for Phase 0 manual-only testing. */
        fun minimal(roundWind: Wind = Wind.EAST, seatWind: Wind = Wind.EAST) = RoundContext(
            roundWind = roundWind,
            seatWind = seatWind,
            honbaCount = 0,
            riichiSticksOnTable = 0,
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList(),
            isRiichi = false,
            isDoubleRiichi = false,
            isIppatsu = false,
            isHaitei = false,
            isHoutei = false,
            isRinshan = false,
            isChankan = false,
        )
    }
}
