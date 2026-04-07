package dev.mahjong.shoujo.domain.engine

import dev.mahjong.shoujo.domain.model.Hand
import dev.mahjong.shoujo.domain.model.RoundContext
import dev.mahjong.shoujo.domain.model.ScoringResult

/**
 * The deterministic Riichi Mahjong scoring engine interface.
 *
 * CONTRACT:
 *  - Pure function: same inputs always produce the same output.
 *  - No side effects, no coroutines, no I/O.
 *  - Zero knowledge of CV, camera, models, or UI.
 *  - Fully testable on the JVM without an Android device.
 *
 * Implementations should throw [InvalidHandException] for structurally invalid input,
 * and return a result with an empty yakuList for valid but no-yaku hands (chombo case).
 */
interface ScoringEngine {
    /**
     * Scores the given hand in its round context.
     *
     * @throws InvalidHandException if the hand is structurally invalid (wrong tile count, etc.)
     */
    fun score(hand: Hand, context: RoundContext): ScoringResult
}

class InvalidHandException(message: String) : Exception(message)
