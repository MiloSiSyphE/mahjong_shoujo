package dev.mahjong.shoujo.domain.engine

import dev.mahjong.shoujo.domain.model.Hand
import dev.mahjong.shoujo.domain.model.Honor
import dev.mahjong.shoujo.domain.model.LimitHand
import dev.mahjong.shoujo.domain.model.MeldType
import dev.mahjong.shoujo.domain.model.NumberSuit
import dev.mahjong.shoujo.domain.model.RoundContext
import dev.mahjong.shoujo.domain.model.ScoringResult
import dev.mahjong.shoujo.domain.model.Tile
import dev.mahjong.shoujo.domain.model.TsumoPayments
import dev.mahjong.shoujo.domain.model.Wind
import dev.mahjong.shoujo.domain.model.Yaku
import dev.mahjong.shoujo.domain.model.YakuResult

/**
 * Deterministic Riichi Mahjong scoring engine — Phase 0 / early Phase 1.
 *
 * ## Supported yaku
 *   RIICHI, DOUBLE_RIICHI, IPPATSU, MENZEN_TSUMO
 *   TANYAO, PINFU, CHIITOITSU, YAKUHAI, IIPEIKO
 *   HAITEI, HOUTEI, RINSHAN, CHANKAN (situational, 1-han each)
 *
 * ## Supported decomposition types
 *   STANDARD (4 melds + 1 pair), CHIITOITSU (7 pairs)
 *   TODO: KOKUSHI MUSOU
 *
 * ## Fu rules supported
 *   Base fu (30 ron / 20 tsumo), tsumo bonus (+2), pinfu-tsumo fixed (20),
 *   chiitoitsu fixed (25), wait-type fu, pair fu (yakuhai pair = 2),
 *   meld fu (triplet/quad, open vs closed, simple vs terminal/honour)
 *   TODO: edge cases for added-kan fu treatment
 *
 * ## Dora
 *   Normal dora indicator mapping, akadora (isAkadora flag), ura-dora (riichi only)
 *
 * ## Points
 *   Formula-based for non-limit hands; fixed tables for MANGAN through YAKUMAN.
 *   Honba and riichi-stick adjustments applied.
 *
 * ## Not yet implemented
 *   Yakuman detection, kokushi, ryanpeiko, broader structural yaku (chinitsu, etc.)
 */
class ScoringEngineImpl : ScoringEngine {

    override fun score(hand: Hand, context: RoundContext): ScoringResult {
        validateHand(hand)

        val chiitoitsuDecomp = decomposeChiitoitsu(hand)
        val standardDecomps  = decompose(hand)

        data class Candidate(
            val decomp:       Decomposition,
            val yakuList:     List<YakuResult>,
            val totalHan:     Int,
            val fu:           Int,
            val doraCount:    Int,   // normal + akadora
            val uraDoraCount: Int,
        )

        val isDealer = context.seatWind == Wind.EAST

        fun buildCandidate(decomp: Decomposition): Candidate? {
            val yakuList = detectYaku(decomp, hand, context)
            if (yakuList.isEmpty()) return null

            val normalDora = countDora(hand, context)
            val akaDora    = countAkadora(hand)
            val uraDora    = countUraDora(hand, context)
            val totalDora  = normalDora + akaDora + uraDora

            val baseHan  = yakuList.sumOf { it.han }
            val totalHan = baseHan + totalDora

            val fu = when {
                decomp.type == DecompositionType.CHIITOITSU                      -> 25
                yakuList.any { it.yaku == Yaku.PINFU } && hand.isTsumo           -> 20
                else                                                               -> calculateFu(decomp, hand, context)
            }
            return Candidate(decomp, yakuList, totalHan, fu, normalDora + akaDora, uraDora)
        }

        val candidates = buildList {
            chiitoitsuDecomp?.let { buildCandidate(it)?.let(::add) }
            standardDecomps.forEach { buildCandidate(it)?.let(::add) }
        }

        if (candidates.isEmpty()) {
            return ScoringResult(
                yakuList      = emptyList(),
                totalHan      = 0,
                fu            = null,
                pointsWon     = 0,
                ronPayment    = null,
                tsumoPayments = null,
                limitHand     = null,
                doraCount     = 0,
                uraDoraCount  = 0,
                explanation   = listOf("No valid yaku found — chombo hand"),
            )
        }

        // Pick best candidate: maximise points won (handles mangan edge cases)
        val best = candidates.maxWith(
            compareBy { lookupPoints(it.totalHan, it.fu, isDealer, hand.isTsumo).totalWon }
        )

        val baseRow     = lookupPoints(best.totalHan, best.fu, isDealer, hand.isTsumo)
        val honbaBonus  = context.honbaCount * 100
        val numPayers   = if (hand.isTsumo) 3 else 1
        val totalHonba  = honbaBonus * numPayers
        val riichiBonus = context.riichiSticksOnTable * 1000

        val ronPayment    = baseRow.ronPayment?.plus(honbaBonus)
        val tsumoPayments = baseRow.tsumoPayments?.let {
            TsumoPayments(it.dealerPays + honbaBonus, it.nonDealerPays + honbaBonus)
        }
        val totalWon = baseRow.totalWon + totalHonba + riichiBonus

        val explanation = buildList {
            add("Decomposition: ${best.decomp.type}")
            best.yakuList.forEach { add("  ${it.yaku.englishName}: ${it.han} han") }
            if (best.doraCount > 0) add("  Dora: ${best.doraCount}")
            if (best.uraDoraCount > 0) add("  Ura-dora: ${best.uraDoraCount}")
            add("Total: ${best.totalHan} han, ${best.fu} fu")
            add("Points won: $totalWon")
        }

        return ScoringResult(
            yakuList      = best.yakuList,
            totalHan      = best.totalHan,
            fu            = best.fu,
            pointsWon     = totalWon,
            ronPayment    = ronPayment,
            tsumoPayments = tsumoPayments,
            limitHand     = determineLimitHand(best.totalHan),
            doraCount     = best.doraCount,
            uraDoraCount  = best.uraDoraCount,
            explanation   = explanation,
        )
    }

    // ── validation ────────────────────────────────────────────────────────────

    private fun validateHand(hand: Hand) {
        if (hand.totalTileCount != 14) {
            throw InvalidHandException(
                "Hand must contain exactly 14 tiles, got ${hand.totalTileCount}"
            )
        }
    }

    // ── decomposition ─────────────────────────────────────────────────────────

    /**
     * Enumerate all ways to partition (closedTiles + winningTile) into sequences
     * and triplets, plus exactly one pair.  Open melds are included as-is.
     *
     * Returns an empty list when no valid decomposition exists.
     */
    private fun decompose(hand: Hand): List<Decomposition> {
        val pool = (hand.closedTiles + hand.winningTile).sortedBy { it.sortKey }
        val openGroups = hand.openMelds.map { it.toOpenGroup() }

        val closedGroupLists = searchDecompositions(pool, pairFound = false, accumulated = emptyList())

        return closedGroupLists
            .distinctBy { groups ->
                // Deduplicate logically identical decompositions that differ only in akadora instances
                groups.map { it.canonicalKey() }.sorted().joinToString("|")
            }
            .map { closedGroups ->
                val pair       = closedGroups.filterIsInstance<ParsedGroup.Pair>().first()
                val allGroups  = openGroups + closedGroups
                val waitType   = determineWait(closedGroups, hand.winningTile)
                Decomposition(DecompositionType.STANDARD, allGroups, pair, waitType)
            }
    }

    /**
     * Recursive backtracking search over a sorted tile list.
     * Always processes the first (lowest-order) tile to ensure progress.
     */
    private fun searchDecompositions(
        remaining:   List<Tile>,
        pairFound:   Boolean,
        accumulated: List<ParsedGroup>,
    ): List<List<ParsedGroup>> {
        if (remaining.isEmpty()) {
            return if (pairFound) listOf(accumulated) else emptyList()
        }

        val first   = remaining[0]
        val rest    = remaining.drop(1)
        val results = mutableListOf<List<ParsedGroup>>()

        // Option 1 — pair (only if no pair yet)
        if (!pairFound) {
            rest.removeFirst { it.sameKind(first) }?.let { (_, afterPair) ->
                results += searchDecompositions(afterPair, true, accumulated + ParsedGroup.Pair(first))
            }
        }

        // Option 2 — triplet
        rest.removeFirst { it.sameKind(first) }?.let { (_, after1) ->
            after1.removeFirst { it.sameKind(first) }?.let { (_, after2) ->
                results += searchDecompositions(after2, pairFound, accumulated + ParsedGroup.Triplet(first, isOpen = false))
            }
        }

        // Option 3 — sequence (number tiles only, 1..7 as starting number)
        if (first is Tile.NumberTile && first.number <= 7) {
            rest.removeFirst { it is Tile.NumberTile && it.suit == first.suit && it.number == first.number + 1 }
                ?.let { (t2, after1) ->
                    after1.removeFirst { it is Tile.NumberTile && it.suit == first.suit && it.number == first.number + 2 }
                        ?.let { (t3, after2) ->
                            results += searchDecompositions(
                                after2, pairFound,
                                accumulated + ParsedGroup.Sequence(first, t2, t3)
                            )
                        }
                }
        }

        return results
    }

    /**
     * Try to decompose as chiitoitsu (7 distinct pairs).
     * Four-of-a-kind counts as one pair, not two — standard Riichi rule.
     */
    private fun decomposeChiitoitsu(hand: Hand): Decomposition? {
        if (hand.openMelds.isNotEmpty()) return null
        val allTiles = hand.closedTiles + hand.winningTile
        if (allTiles.size != 14) return null

        val grouped = allTiles.groupBy { it.kindKey }
        if (grouped.size != 7 || grouped.any { (_, v) -> v.size != 2 }) return null

        val pairs = grouped.map { (_, tiles) -> ParsedGroup.Pair(tiles.first()) }
        val pairForWinTile = pairs.first { it.tile.sameKind(hand.winningTile) }
        return Decomposition(DecompositionType.CHIITOITSU, pairs, pairForWinTile, WaitType.TANKI)
    }

    /** TODO(Phase 2): implement kokushi musou decomposition. */
    @Suppress("UnusedPrivateMember")
    private fun decomposeKokushi(@Suppress("UNUSED_PARAMETER") hand: Hand): Decomposition? = null

    // ── wait type ─────────────────────────────────────────────────────────────

    /**
     * Determine the wait type for the winning tile within the CLOSED groups of a
     * decomposition.  Open meld groups are irrelevant for wait detection.
     *
     * Returns the first matching wait found; ambiguous waits in multi-decomposition
     * hands are resolved by picking the best-scoring decomposition upstream.
     */
    private fun determineWait(closedGroups: List<ParsedGroup>, winningTile: Tile): WaitType {
        for (group in closedGroups) {
            when (group) {
                is ParsedGroup.Pair -> {
                    if (group.tile.sameKind(winningTile)) return WaitType.TANKI
                }
                is ParsedGroup.Sequence -> {
                    val t1 = group.t1; val t2 = group.t2; val t3 = group.t3
                    val idx = listOf(t1, t2, t3).indexOfFirst { it.sameKind(winningTile) }
                    if (idx >= 0) {
                        return when (idx) {
                            1 -> WaitType.KANCHAN  // middle tile — kanchan
                            0 -> {
                                // Smallest tile — had (t2, t3); penchan iff t3 ends at 9
                                val end = (t3 as? Tile.NumberTile)?.number
                                if (end == 9) WaitType.PENCHAN else WaitType.RYANMEN
                            }
                            else -> {
                                // Largest tile — had (t1, t2); penchan iff t1 starts at 1
                                val start = (t1 as? Tile.NumberTile)?.number
                                if (start == 1) WaitType.PENCHAN else WaitType.RYANMEN
                            }
                        }
                    }
                }
                is ParsedGroup.Triplet -> {
                    // Winning tile completed a triplet → was a shanpon (double-pair) wait
                    if (group.tile.sameKind(winningTile)) return WaitType.SHANPON
                }
                is ParsedGroup.Quad -> { /* kans do not contribute to wait determination */ }
            }
        }
        return WaitType.RYANMEN // fallback — should not be reached in a valid decomposition
    }


    // ── yaku detection ────────────────────────────────────────────────────────

    /**
     * Detect all applicable yaku for the given decomposition.
     * Returns an empty list when the hand has no valid yaku (chombo).
     *
     * Supported: RIICHI, DOUBLE_RIICHI, IPPATSU, MENZEN_TSUMO,
     *            TANYAO, PINFU, CHIITOITSU, YAKUHAI, IIPEIKO,
     *            HAITEI, HOUTEI, RINSHAN, CHANKAN
     *
     * TODO: TOITOI, CHINITSU, HONITSU, SANANKOU, and all yakuman
     */
    private fun detectYaku(
        decomp:  Decomposition,
        hand:    Hand,
        context: RoundContext,
    ): List<YakuResult> {
        val yaku = mutableListOf<YakuResult>()

        // ── situational yaku (always checked regardless of decomp type) ─────
        when {
            context.isDoubleRiichi && hand.isClosed ->
                yaku += YakuResult(Yaku.DOUBLE_RIICHI, Yaku.DOUBLE_RIICHI.closedHan, false)
            context.isRiichi && hand.isClosed ->
                yaku += YakuResult(Yaku.RIICHI, Yaku.RIICHI.closedHan, false)
        }
        if (context.isIppatsu && hand.isClosed)
            yaku += YakuResult(Yaku.IPPATSU, Yaku.IPPATSU.closedHan, false)
        if (hand.isTsumo && hand.isClosed)
            yaku += YakuResult(Yaku.MENZEN_TSUMO, Yaku.MENZEN_TSUMO.closedHan, false)
        if (context.isHaitei)
            yaku += YakuResult(Yaku.HAITEI_RAOYUE, 1, !hand.isClosed)
        if (context.isHoutei)
            yaku += YakuResult(Yaku.HOUTEI_RAOYUI, 1, !hand.isClosed)
        if (context.isRinshan)
            yaku += YakuResult(Yaku.RINSHAN_KAIHOU, 1, !hand.isClosed)
        if (context.isChankan)
            yaku += YakuResult(Yaku.CHANKAN, 1, !hand.isClosed)

        // ── chiitoitsu (structural, returns early — doesn't combine with below) ─
        if (decomp.type == DecompositionType.CHIITOITSU) {
            yaku += YakuResult(Yaku.CHIITOITSU, Yaku.CHIITOITSU.closedHan, false)
            return yaku
        }

        // ── all-tiles check (for tanyao) ─────────────────────────────────────
        val allTiles = decomp.groups.flatMap { it.tiles }

        // TANYAO — all tiles are simples (2–8 number tiles, no terminals/honours)
        if (allTiles.all { it.isSimple }) {
            val isOpen = !hand.isClosed
            yaku += YakuResult(Yaku.TANYAO, if (isOpen) Yaku.TANYAO.openHan else Yaku.TANYAO.closedHan, isOpen)
        }

        // PINFU — closed, all sequences, non-yakuhai pair, ryanmen wait
        val nonPairGroups = decomp.groups.filterNot { it is ParsedGroup.Pair }
        if (hand.isClosed &&
            nonPairGroups.all { it is ParsedGroup.Sequence } &&
            !isYakuhaiTile(decomp.pair.tile, context) &&
            decomp.waitType == WaitType.RYANMEN
        ) {
            yaku += YakuResult(Yaku.PINFU, Yaku.PINFU.closedHan, false)
        }

        // YAKUHAI — per qualifying triplet/quad (doubles if wind matches both seat and round)
        for (group in decomp.groups) {
            val (tile, open) = when (group) {
                is ParsedGroup.Triplet -> group.tile to group.isOpen
                is ParsedGroup.Quad    -> group.tile to group.isOpen
                else                   -> continue
            }
            if (tile !is Tile.HonorTile) continue

            val count = yakuhaiCount(tile.honor, context)
            if (count > 0) {
                val han = if (open) Yaku.YAKUHAI.openHan else Yaku.YAKUHAI.closedHan
                repeat(count) { yaku += YakuResult(Yaku.YAKUHAI, han, open) }
            }
        }

        // IIPEIKO — closed hand with at least one pair of identical sequences
        if (hand.isClosed) {
            val seqs = nonPairGroups.filterIsInstance<ParsedGroup.Sequence>()
            val keys = seqs.map { Triple(
                (it.t1 as? Tile.NumberTile)?.suit?.ordinal ?: -1,
                (it.t1 as? Tile.NumberTile)?.number       ?: -1,
                (it.t3 as? Tile.NumberTile)?.number       ?: -1,
            )}
            if (keys.size != keys.distinct().size) {
                // TODO(Phase 1): detect RYANPEIKO (two pairs of identical sequences = 3 han)
                //   and award that instead of iipeiko when applicable.
                yaku += YakuResult(Yaku.IIPEIKO, Yaku.IIPEIKO.closedHan, false)
            }
        }

        return yaku
    }

    /**
     * How many times a given honour tile qualifies as yakuhai.
     * Dragons always return 1. Winds return 0, 1, or 2 (double-wind counts twice).
     */
    private fun yakuhaiCount(honor: Honor, context: RoundContext): Int = when (honor) {
        Honor.HAKU, Honor.HATSU, Honor.CHUN -> 1
        else -> {
            val wind = honor.toWind() ?: return 0
            (if (wind == context.seatWind)  1 else 0) +
            (if (wind == context.roundWind) 1 else 0)
        }
    }

    private fun isYakuhaiTile(tile: Tile, context: RoundContext): Boolean =
        tile is Tile.HonorTile && yakuhaiCount(tile.honor, context) > 0


    // ── fu calculation ────────────────────────────────────────────────────────

    /**
     * Calculate fu for a standard decomposition.
     *
     * Chiitoitsu (25 fu) and pinfu-tsumo (20 fu) are handled by the caller
     * before this method is invoked.
     *
     * Base fu: 30 for ron (open or closed), 20 for tsumo.
     * Tsumo bonus: +2 (not applied for pinfu — caller handles that override).
     * Wait fu: 0 for ryanmen/shanpon, 2 for penchan/kanchan/tanki.
     * Pair fu: 2 for yakuhai pair, 0 otherwise.
     * Meld fu: see [groupFu] for per-group values.
     * Final result: rounded up to nearest 10.
     */
    private fun calculateFu(decomp: Decomposition, hand: Hand, context: RoundContext): Int {
        val baseFu     = if (hand.isTsumo) 20 else 30
        val tsumoBonus = if (hand.isTsumo) 2 else 0
        val waitFu     = when (decomp.waitType) {
            WaitType.RYANMEN, WaitType.SHANPON               -> 0
            WaitType.KANCHAN, WaitType.PENCHAN, WaitType.TANKI -> 2
        }
        val pairFu  = if (isYakuhaiTile(decomp.pair.tile, context)) 2 else 0
        val meldsFu = decomp.groups.filterNot { it is ParsedGroup.Pair }.sumOf { groupFu(it) }

        return roundUp10(baseFu + tsumoBonus + waitFu + pairFu + meldsFu)
    }

    /** Fu contributed by a single meld group. */
    private fun groupFu(group: ParsedGroup): Int {
        val isSimple: (Tile) -> Boolean = { it.isSimple }
        return when (group) {
            is ParsedGroup.Sequence -> 0
            is ParsedGroup.Pair     -> 0  // pair fu is handled separately
            is ParsedGroup.Triplet  -> when {
                group.isOpen  && isSimple(group.tile)  -> 2
                group.isOpen  && !isSimple(group.tile) -> 4
                !group.isOpen && isSimple(group.tile)  -> 4
                else                                   -> 8   // closed terminal/honour
            }
            is ParsedGroup.Quad     -> when {
                group.isOpen  && isSimple(group.tile)  -> 8
                group.isOpen  && !isSimple(group.tile) -> 16
                !group.isOpen && isSimple(group.tile)  -> 16
                else                                   -> 32  // closed terminal/honour
            }
        }
    }

    // ── dora counting ─────────────────────────────────────────────────────────

    /** Count normal dora tiles in the hand using indicator → actual-dora mapping. */
    private fun countDora(hand: Hand, context: RoundContext): Int {
        val doraTiles = context.doraIndicators.map { doraFromIndicator(it) }
        val allTiles  = hand.allTiles
        return doraTiles.sumOf { dora -> allTiles.count { it.sameKind(dora) } }
    }

    /** Count red-five (akadora) tiles in the hand. */
    private fun countAkadora(hand: Hand): Int =
        hand.allTiles.count { it is Tile.NumberTile && it.isAkadora }

    /**
     * Count ura-dora tiles.  Ura-dora only applies when riichi was declared.
     * TODO(Phase 2): verify ura-dora indicators are wired from the UI.
     */
    private fun countUraDora(hand: Hand, context: RoundContext): Int {
        if (!context.isRiichi && !context.isDoubleRiichi) return 0
        val uraDoraTiles = context.uraDoraIndicators.map { doraFromIndicator(it) }
        val allTiles     = hand.allTiles
        return uraDoraTiles.sumOf { dora -> allTiles.count { it.sameKind(dora) } }
    }

    /**
     * Map a dora indicator tile to the actual dora tile.
     *   Numbers: N → N+1 (9 wraps to 1)
     *   Winds:   East → South → West → North → East
     *   Dragons: Haku → Hatsu → Chun → Haku
     */
    private fun doraFromIndicator(indicator: Tile): Tile = when (indicator) {
        is Tile.NumberTile -> Tile.NumberTile(
            indicator.suit,
            if (indicator.number == 9) 1 else indicator.number + 1,
        )
        is Tile.HonorTile  -> Tile.HonorTile(
            when (indicator.honor) {
                Honor.EAST  -> Honor.SOUTH
                Honor.SOUTH -> Honor.WEST
                Honor.WEST  -> Honor.NORTH
                Honor.NORTH -> Honor.EAST
                Honor.HAKU  -> Honor.HATSU
                Honor.HATSU -> Honor.CHUN
                Honor.CHUN  -> Honor.HAKU
            }
        )
    }

    // ── point lookup ──────────────────────────────────────────────────────────

    /**
     * Look up the points row for a given han + fu.
     *
     * Uses the standard formula:
     *   basicPoints = fu × 2^(han+2)
     *   Non-dealer ron = basicPoints × 4, rounded up to nearest 100
     *   Dealer ron     = basicPoints × 6, rounded up to nearest 100
     *   Tsumo dealer-pays = basicPoints × 2, rounded up to nearest 100
     *   Tsumo other-pays  = basicPoints × 1, rounded up to nearest 100
     *
     * Mangan applies when the computed payment ≥ 8000 (non-dealer) / 12000 (dealer).
     * Returns a [PointsRow] that excludes honba and riichi-stick adjustments.
     */
    private fun lookupPoints(han: Int, fu: Int, isDealer: Boolean, isTsumo: Boolean): PointsRow {
        val limitHand = determineLimitHand(han)

        if (limitHand != null) {
            return limitHandRow(limitHand, isDealer, isTsumo)
        }

        val basicPoints = fu * (1 shl (han + 2))  // fu × 4 × 2^han

        // Promote to mangan if the formula would exceed the mangan threshold
        val nonDealerRonValue = roundUp100(basicPoints * 4)
        if (nonDealerRonValue >= MANGAN_NON_DEALER_RON) {
            return limitHandRow(LimitHand.MANGAN, isDealer, isTsumo)
        }

        return if (isTsumo) {
            if (isDealer) {
                val each  = roundUp100(basicPoints * 2)
                PointsRow(each * 3, null, TsumoPayments(each, each))
            } else {
                val dealerPays = roundUp100(basicPoints * 2)
                val otherPays  = roundUp100(basicPoints)
                PointsRow(dealerPays + otherPays * 2, null, TsumoPayments(dealerPays, otherPays))
            }
        } else {
            val ron = if (isDealer) roundUp100(basicPoints * 6) else roundUp100(basicPoints * 4)
            PointsRow(ron, ron, null)
        }
    }

    private fun determineLimitHand(han: Int): LimitHand? = when {
        han >= Yaku.YAKUMAN_HAN -> LimitHand.YAKUMAN
        han >= 11               -> LimitHand.SANBAIMAN
        han >= 8                -> LimitHand.BAIMAN
        han >= 6                -> LimitHand.HANEMAN
        han >= 5                -> LimitHand.MANGAN
        else                    -> null
    }

    private fun limitHandRow(limit: LimitHand, isDealer: Boolean, isTsumo: Boolean): PointsRow {
        val (ndRon, dRon, ndTD, ndTO, dTE) = LIMIT_HAND_POINTS.getValue(limit)
        return if (isTsumo) {
            if (isDealer) PointsRow(dTE * 3, null, TsumoPayments(dTE, dTE))
            else          PointsRow(ndTD + ndTO * 2, null, TsumoPayments(ndTD, ndTO))
        } else {
            val ron = if (isDealer) dRon else ndRon
            PointsRow(ron, ron, null)
        }
    }

    // ── utility ───────────────────────────────────────────────────────────────

    private fun roundUp10(n: Int)  = if (n % 10 == 0) n else (n / 10 + 1) * 10
    private fun roundUp100(n: Int) = if (n % 100 == 0) n else (n / 100 + 1) * 100

    /** Remove and return the first element matching [predicate], or null. */
    private fun List<Tile>.removeFirst(predicate: (Tile) -> Boolean): Pair<Tile, List<Tile>>? {
        val idx = indexOfFirst(predicate)
        if (idx == -1) return null
        return this[idx] to (take(idx) + drop(idx + 1))
    }

    // ── Tile extensions ───────────────────────────────────────────────────────

    /**
     * Sort key that ignores isAkadora so red-fives sort alongside their normal counterparts.
     */
    private val Tile.sortKey: Int get() = when (this) {
        is Tile.NumberTile -> suit.ordinal * 9 + (number - 1)
        is Tile.HonorTile  -> 27 + honor.ordinal
    }

    /**
     * Grouping key (ignores isAkadora).  Used for chiitoitsu grouping and deduplication.
     */
    private val Tile.kindKey: String get() = when (this) {
        is Tile.NumberTile -> "${suit.ordinal}-$number"
        is Tile.HonorTile  -> "H-${honor.ordinal}"
    }

    /**
     * True when two tiles represent the same mahjong tile type (ignores isAkadora).
     */
    private fun Tile.sameKind(other: Tile): Boolean = when {
        this is Tile.NumberTile && other is Tile.NumberTile ->
            suit == other.suit && number == other.number
        this is Tile.HonorTile && other is Tile.HonorTile ->
            honor == other.honor
        else -> false
    }

    /** Convenience: all tiles in the hand (closed + open melds + winning tile). */
    private val Hand.allTiles: List<Tile>
        get() = closedTiles + openMelds.flatMap { it.tiles } + winningTile

    // ── Meld extension ────────────────────────────────────────────────────────

    private fun dev.mahjong.shoujo.domain.model.Meld.toOpenGroup(): ParsedGroup {
        val sorted = tiles.sortedBy { it.sortKey }
        return when (type) {
            MeldType.CHI        -> ParsedGroup.Sequence(sorted[0], sorted[1], sorted[2])
            MeldType.PON        -> ParsedGroup.Triplet(sorted[0], isOpen = true)
            MeldType.OPEN_KAN   -> ParsedGroup.Quad(sorted[0], isOpen = true)
            MeldType.CLOSED_KAN -> ParsedGroup.Quad(sorted[0], isOpen = false)
            // Added-kan tile ownership is open; treated as open quad for fu
            MeldType.ADDED_KAN  -> ParsedGroup.Quad(sorted[0], isOpen = true)
        }
    }

    // ── Honor extension ───────────────────────────────────────────────────────

    private fun Honor.toWind(): Wind? = when (this) {
        Honor.EAST  -> Wind.EAST
        Honor.SOUTH -> Wind.SOUTH
        Honor.WEST  -> Wind.WEST
        Honor.NORTH -> Wind.NORTH
        else        -> null
    }

    // ── constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val MANGAN_NON_DEALER_RON = 8000

        /** Fixed payment amounts for each limit-hand tier (non-dealer ron, dealer ron,
         *  non-dealer-wins-tsumo dealer pays, non-dealer-wins-tsumo other pays,
         *  dealer-wins-tsumo each pays). */
        private data class LimitAmounts(
            val nonDealerRon: Int, val dealerRon:   Int,
            val ndTsumoD:     Int, val ndTsumoO:    Int,
            val dTsumoEach:   Int,
        )

        private val LIMIT_HAND_POINTS = mapOf(
            LimitHand.MANGAN        to LimitAmounts( 8000, 12000, 4000, 2000,  4000),
            LimitHand.HANEMAN       to LimitAmounts(12000, 18000, 6000, 3000,  6000),
            LimitHand.BAIMAN        to LimitAmounts(16000, 24000, 8000, 4000,  8000),
            LimitHand.SANBAIMAN     to LimitAmounts(24000, 36000,12000, 6000, 12000),
            LimitHand.YAKUMAN       to LimitAmounts(32000, 48000,16000, 8000, 16000),
            LimitHand.DOUBLE_YAKUMAN to LimitAmounts(64000, 96000,32000,16000, 32000),
        )
    }
}


// ── Internal decomposition types ─────────────────────────────────────────────

internal sealed class ParsedGroup {
    abstract val tiles: List<Tile>

    /** A two-tile pair — stores one representative tile instance. */
    data class Pair(val tile: Tile) : ParsedGroup() {
        override val tiles get() = listOf(tile, tile)
    }

    /** An ordered sequence of three consecutive number tiles. */
    data class Sequence(val t1: Tile, val t2: Tile, val t3: Tile) : ParsedGroup() {
        override val tiles get() = listOf(t1, t2, t3)
    }

    /** A triplet (pon or closed set). */
    data class Triplet(val tile: Tile, val isOpen: Boolean) : ParsedGroup() {
        override val tiles get() = listOf(tile, tile, tile)
    }

    /** A quad (kan — open or closed). */
    data class Quad(val tile: Tile, val isOpen: Boolean) : ParsedGroup() {
        override val tiles get() = listOf(tile, tile, tile, tile)
    }

    /** A canonical string key that ignores akadora, for deduplication. */
    fun canonicalKey(): String {
        fun Tile.k() = when (this) {
            is Tile.NumberTile -> "${suit.ordinal}-$number"
            is Tile.HonorTile  -> "H-${honor.ordinal}"
        }
        return when (this) {
            is Pair     -> "P:${tile.k()}"
            is Sequence -> "S:${t1.k()}-${t2.k()}-${t3.k()}"
            is Triplet  -> "T:${tile.k()}:${if (isOpen) "O" else "C"}"
            is Quad     -> "Q:${tile.k()}:${if (isOpen) "O" else "C"}"
        }
    }
}

internal enum class WaitType { RYANMEN, SHANPON, KANCHAN, PENCHAN, TANKI }

internal data class Decomposition(
    val type:     DecompositionType,
    val groups:   List<ParsedGroup>,  // all groups including open melds
    val pair:     ParsedGroup.Pair,
    val waitType: WaitType,
)

internal enum class DecompositionType { STANDARD, CHIITOITSU, KOKUSHI }

internal data class PointsRow(
    val totalWon:      Int,
    val ronPayment:    Int?,
    val tsumoPayments: TsumoPayments?,
)
