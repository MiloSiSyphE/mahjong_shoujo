package dev.mahjong.shoujo.domain.engine

import dev.mahjong.shoujo.domain.model.Hand
import dev.mahjong.shoujo.domain.model.LimitHand
import dev.mahjong.shoujo.domain.model.RoundContext
import dev.mahjong.shoujo.domain.model.ScoringResult
import dev.mahjong.shoujo.domain.model.TsumoPayments
import dev.mahjong.shoujo.domain.model.YakuResult

/**
 * Deterministic Riichi Mahjong scoring engine.
 *
 * Phase 0 stub — returns a structurally valid but incomplete result.
 * Replace method bodies with correct implementation, working test-first.
 *
 * Implementation order (suggested):
 *   1. Hand decomposition into melds (standard 4-meld + pair)
 *   2. Chiitoitsu decomposition
 *   3. Kokushi decomposition
 *   4. Fu calculation
 *   5. Yaku detection (start with tanyao, pinfu, riichi — add one at a time)
 *   6. Dora counting
 *   7. Limit hand detection and point table lookup
 *   8. Honba / riichi-stick adjustments
 *
 * Do NOT add Android or CV dependencies to this file.
 */
class ScoringEngineImpl : ScoringEngine {

    override fun score(hand: Hand, context: RoundContext): ScoringResult {
        validateHand(hand)

        // TODO(Phase 0): implement full yaku detection
        // TODO(Phase 0): implement fu calculation
        // TODO(Phase 0): implement point table lookup
        // TODO(Phase 0): implement honba / riichi-stick bonuses

        return ScoringResult(
            yakuList = emptyList(),        // TODO
            totalHan = 0,                  // TODO
            fu = null,                     // TODO
            pointsWon = 0,                 // TODO
            ronPayment = null,             // TODO
            tsumoPayments = null,          // TODO
            limitHand = null,              // TODO
            doraCount = 0,                 // TODO
            uraDoraCount = 0,              // TODO
            explanation = listOf("Scoring engine not yet implemented — Phase 0 stub"),
        )
    }

    private fun validateHand(hand: Hand) {
        if (hand.totalTileCount != 14) {
            throw InvalidHandException(
                "Hand must contain exactly 14 tiles, got ${hand.totalTileCount}"
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers — implement these incrementally, one at a time, test-first
    // ---------------------------------------------------------------------------

    /** Enumerate all ways to partition closedTiles + winningTile into 4 melds + 1 pair. */
    private fun decompose(hand: Hand): List<Decomposition> {
        // TODO(Phase 0): implement standard decomposition
        return emptyList()
    }

    /** Try to decompose as chiitoitsu (7 pairs). */
    private fun decomposeChiitoitsu(hand: Hand): Decomposition? {
        // TODO(Phase 0): implement chiitoitsu decomposition
        return null
    }

    /** Try to decompose as kokushi musou. */
    private fun decomposeKokushi(hand: Hand): Decomposition? {
        // TODO(Phase 0): implement kokushi decomposition
        return null
    }

    private fun countDora(hand: Hand, context: RoundContext): Int {
        // TODO(Phase 0): implement dora counting (indicators → actual dora tile)
        return 0
    }

    private fun calculateFu(decomp: Decomposition, hand: Hand, context: RoundContext): Int {
        // TODO(Phase 0): implement fu calculation per decomposition
        return 30 // placeholder — minimum valid fu for a winning hand
    }

    private fun detectYaku(
        decomp: Decomposition,
        hand: Hand,
        context: RoundContext,
    ): List<YakuResult> {
        // TODO(Phase 0): implement yaku detection
        return emptyList()
    }

    private fun lookupPoints(han: Int, fu: Int?, isDealer: Boolean, isTsumo: Boolean): PointsRow {
        // TODO(Phase 0): implement standard point table lookup
        return PointsRow(0, null, null)
    }
}

// ---------------------------------------------------------------------------
// Internal decomposition types — not part of the public API
// ---------------------------------------------------------------------------

internal data class Decomposition(
    val type: DecompositionType,
    // TODO: add meld list, pair, wait type, etc.
)

internal enum class DecompositionType { STANDARD, CHIITOITSU, KOKUSHI }

internal data class PointsRow(
    val totalWon: Int,
    val ronPayment: Int?,
    val tsumoPayments: TsumoPayments?,
)
