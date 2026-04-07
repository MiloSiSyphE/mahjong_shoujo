package dev.mahjong.shoujo.domain.model

/**
 * The final, fully-explained output of the scoring engine.
 *
 * The scoring engine must produce this without knowing anything about CV or capture type.
 */
data class ScoringResult(
    val yakuList: List<YakuResult>,

    /** Total han count (sum of yaku + dora, capped by limit hands). */
    val totalHan: Int,

    /**
     * Fu count (if applicable). Null for limit hands (mangan and above)
     * where fu is irrelevant to the point calculation.
     */
    val fu: Int?,

    /** Point value to award the winning player (the amount the winner gains total). */
    val pointsWon: Int,

    /** How many points each non-winning player pays. Null for tsumo (see tsumoPayments). */
    val ronPayment: Int?,

    /** For tsumo: how many points dealer and non-dealer pay respectively. */
    val tsumoPayments: TsumoPayments?,

    /** The hand limit category (null = not a limit hand). */
    val limitHand: LimitHand?,

    /** Dora tiles found in the hand (for display). */
    val doraCount: Int,
    val uraDoraCount: Int,

    /** Human-readable step-by-step explanation. */
    val explanation: List<String>,
) {
    val isYakuman: Boolean get() = limitHand == LimitHand.YAKUMAN || limitHand == LimitHand.DOUBLE_YAKUMAN
}

data class TsumoPayments(
    /** Points paid by the dealer (if non-dealer wins). */
    val dealerPays: Int,
    /** Points paid by each non-dealer. */
    val nonDealerPays: Int,
)

enum class LimitHand(val japaneseName: String, val multiplier: Int) {
    MANGAN   ("満貫",   1),
    HANEMAN  ("跳満",   2),
    BAIMAN   ("倍満",   3),
    SANBAIMAN("三倍満",  4),
    YAKUMAN  ("役満",   4), // base yakuman = same multiplier as sanbaiman historically
    DOUBLE_YAKUMAN("ダブル役満", 8),
}

/** The intermediate fu breakdown, preserved for explanation display. */
data class FuBreakdown(
    val baseFu: Int,
    val waitFu: Int,
    val tilesFu: Int,
    val meldsFu: Int,
    val closedRonBonus: Int,
    val tsumoBonus: Int,
) {
    val total: Int get() = (baseFu + waitFu + tilesFu + meldsFu + closedRonBonus + tsumoBonus)
        .roundUpToNearest10()

    private fun Int.roundUpToNearest10() = if (this % 10 == 0) this else (this / 10 + 1) * 10
}
