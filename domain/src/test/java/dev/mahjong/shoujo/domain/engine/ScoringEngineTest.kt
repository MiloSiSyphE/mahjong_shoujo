package dev.mahjong.shoujo.domain.engine

import dev.mahjong.shoujo.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ScoringEngineImpl.
 * Written test-first: these tests define the expected behaviour.
 */
class ScoringEngineTest {

    private val engine: ScoringEngine = ScoringEngineImpl()

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun man(n: Int, aka: Boolean = false) = Tile.NumberTile(NumberSuit.MAN, n, aka)
    private fun pin(n: Int, aka: Boolean = false) = Tile.NumberTile(NumberSuit.PIN, n, aka)
    private fun sou(n: Int, aka: Boolean = false) = Tile.NumberTile(NumberSuit.SOU, n, aka)
    private fun east()  = Tile.HonorTile(Honor.EAST)
    private fun south() = Tile.HonorTile(Honor.SOUTH)
    private fun west()  = Tile.HonorTile(Honor.WEST)
    private fun north() = Tile.HonorTile(Honor.NORTH)
    private fun haku()  = Tile.HonorTile(Honor.HAKU)
    private fun hatsu() = Tile.HonorTile(Honor.HATSU)
    private fun chun()  = Tile.HonorTile(Honor.CHUN)

    /** Minimal east-round, east-seat context — no riichi, no dora. */
    private fun ctx(
        roundWind: Wind = Wind.EAST,
        seatWind: Wind = Wind.EAST,
        isRiichi: Boolean = false,
        isDoubleRiichi: Boolean = false,
        isIppatsu: Boolean = false,
        isHaitei: Boolean = false,
        isHoutei: Boolean = false,
        doraIndicators: List<Tile> = emptyList(),
        uraDoraIndicators: List<Tile> = emptyList(),
        honba: Int = 0,
        riichiSticks: Int = 0,
    ) = RoundContext(
        roundWind = roundWind,
        seatWind = seatWind,
        honbaCount = honba,
        riichiSticksOnTable = riichiSticks,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators,
        isRiichi = isRiichi,
        isDoubleRiichi = isDoubleRiichi,
        isIppatsu = isIppatsu,
        isHaitei = isHaitei,
        isHoutei = isHoutei,
        isRinshan = false,
        isChankan = false,
    )

    /** 13 closed tiles + winning tile (tsumo or ron). */
    private fun closedHand(closed13: List<Tile>, winning: Tile, isTsumo: Boolean) =
        Hand(closedTiles = closed13, openMelds = emptyList(), winningTile = winning, isTsumo = isTsumo)

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Hand validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test(expected = InvalidHandException::class)
    fun `invalid hand throws InvalidHandException`() {
        val hand = Hand(
            closedTiles = listOf(man(1), man(2)),
            winningTile = man(3),
            isTsumo = true,
        )
        engine.score(hand, ctx())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Tanyao
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tanyao closed ron — all simples gives 1 han`() {
        // 2m3m4m 5m6m7m 3p4p5p 6p7p8p + pair 9p? No, 9p is terminal.
        // Use: 2m3m4m 5m6m7m 3p4p5p 6p7p8p + pair 5s5s, winning 5s
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        assertTrue("tanyao should be detected", result.yakuList.any { it.yaku == Yaku.TANYAO })
        assertEquals(1, result.yakuList.first { it.yaku == Yaku.TANYAO }.han)
    }

    @Test
    fun `tanyao fails when hand contains terminal`() {
        // 1m2m3m is a sequence with terminal 1m → no tanyao
        val closed = listOf(
            man(1), man(2), man(3),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        assertFalse("tanyao should NOT be detected with terminal", result.yakuList.any { it.yaku == Yaku.TANYAO })
    }

    @Test
    fun `tanyao fails when hand contains honor tile`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            haku(), haku(), haku(),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        // yakuhai (haku triplet) is detected, tanyao is not
        assertFalse("tanyao should NOT be detected with honor tile", result.yakuList.any { it.yaku == Yaku.TANYAO })
        assertTrue("yakuhai should be detected", result.yakuList.any { it.yaku == Yaku.YAKUHAI })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Pinfu
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `pinfu — all sequences, non-yakuhai pair, ryanmen wait`() {
        // 1m2m3m 4m5m6m 7m8m9m 2p3p + pair 5p5p, winning 4p (ryanmen on 2p3p)
        val closed = listOf(
            man(1), man(2), man(3),
            man(4), man(5), man(6),
            man(7), man(8), man(9),
            pin(2), pin(3),
            pin(5), pin(5),
        )
        val result = engine.score(closedHand(closed, pin(4), isTsumo = false), ctx())
        assertTrue("pinfu should be detected", result.yakuList.any { it.yaku == Yaku.PINFU })
        assertEquals(30, result.fu)
    }

    @Test
    fun `pinfu tsumo — fu is 20`() {
        val closed = listOf(
            man(1), man(2), man(3),
            man(4), man(5), man(6),
            man(7), man(8), man(9),
            pin(2), pin(3),
            pin(5), pin(5),
        )
        val result = engine.score(closedHand(closed, pin(4), isTsumo = true), ctx())
        assertTrue("pinfu should be detected on tsumo", result.yakuList.any { it.yaku == Yaku.PINFU })
        assertEquals("pinfu tsumo must be exactly 20 fu", 20, result.fu)
    }

    @Test
    fun `pinfu fails with yakuhai pair`() {
        // Replace pair with east winds (seat=round=east → yakuhai)
        val closed = listOf(
            man(1), man(2), man(3),
            man(4), man(5), man(6),
            man(7), man(8), man(9),
            pin(2), pin(3),
            east(), east(),
        )
        val result = engine.score(closedHand(closed, pin(4), isTsumo = false), ctx())
        assertFalse("pinfu should NOT apply with yakuhai pair", result.yakuList.any { it.yaku == Yaku.PINFU })
    }

    @Test
    fun `pinfu fails with penchan wait`() {
        // 1m2m3m: winning on 3m (position 2 of 1-2-3) → penchan
        val closed = listOf(
            man(1), man(2),
            man(4), man(5), man(6),
            man(7), man(8), man(9),
            pin(1), pin(2), pin(3),
            sou(5), sou(5),
        )
        val result = engine.score(closedHand(closed, man(3), isTsumo = false), ctx())
        assertFalse("pinfu should NOT apply with penchan wait", result.yakuList.any { it.yaku == Yaku.PINFU })
    }

    @Test
    fun `pinfu fails with open hand`() {
        val openMeld = Meld(
            type = MeldType.CHI,
            tiles = listOf(man(1), man(2), man(3)),
            calledTile = man(3),
            calledFrom = 2,
        )
        val closed = listOf(
            man(4), man(5), man(6),
            man(7), man(8), man(9),
            pin(2), pin(3),
            pin(5), pin(5),
        )
        val hand = Hand(closedTiles = closed, openMelds = listOf(openMeld), winningTile = pin(4), isTsumo = false)
        val result = engine.score(hand, ctx())
        assertFalse("pinfu must not apply to open hand", result.yakuList.any { it.yaku == Yaku.PINFU })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Chiitoitsu
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `chiitoitsu — 7 distinct pairs gives 2 han 25 fu`() {
        // 1m1m 3m3m 5m5m 7m7m 2p2p 4p4p + pair 6p, winning 6p
        val closed = listOf(
            man(1), man(1), man(3), man(3), man(5), man(5),
            man(7), man(7), pin(2), pin(2), pin(4), pin(4),
            pin(6),
        )
        val result = engine.score(closedHand(closed, pin(6), isTsumo = false), ctx())
        assertTrue("chiitoitsu should be detected", result.yakuList.any { it.yaku == Yaku.CHIITOITSU })
        assertEquals(2, result.yakuList.first { it.yaku == Yaku.CHIITOITSU }.han)
        assertEquals(25, result.fu)
    }

    @Test
    fun `chiitoitsu requires 7 different kinds — quad does not count as two pairs`() {
        // 1m1m1m1m → only 1 pair, not 2
        val closed = listOf(
            man(1), man(1), man(1), man(1),
            man(3), man(3),
            man(5), man(5),
            man(7), man(7),
            pin(2), pin(2),
            pin(4),
        )
        val result = engine.score(closedHand(closed, pin(4), isTsumo = false), ctx())
        assertFalse("4-of-a-kind should not form two chiitoitsu pairs", result.yakuList.any { it.yaku == Yaku.CHIITOITSU })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Yakuhai
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `yakuhai — triplet of haku gives 1 han`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            haku(), haku(), haku(),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        val yakuhaiResults = result.yakuList.filter { it.yaku == Yaku.YAKUHAI }
        assertEquals("exactly one yakuhai entry", 1, yakuhaiResults.size)
        assertEquals(1, yakuhaiResults[0].han)
    }

    @Test
    fun `yakuhai — triplet of seat wind gives 1 han`() {
        // Seat = SOUTH → triplet of south is yakuhai
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            south(), south(), south(),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx(seatWind = Wind.SOUTH))
        assertTrue("south triplet is yakuhai for south seat", result.yakuList.any { it.yaku == Yaku.YAKUHAI })
    }

    @Test
    fun `yakuhai — wind is neither seat nor round gives no yakuhai`() {
        // Seat = East, Round = East → west wind triplet is not yakuhai
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            west(), west(), west(),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        assertFalse("west triplet is not yakuhai in east round, east seat", result.yakuList.any { it.yaku == Yaku.YAKUHAI })
    }

    @Test
    fun `yakuhai — double wind (seat equals round) gives 2 han`() {
        // East round, East seat → east triplet = 2 yakuhai
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            east(), east(), east(),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx(roundWind = Wind.EAST, seatWind = Wind.EAST))
        val yakuhaiHan = result.yakuList.filter { it.yaku == Yaku.YAKUHAI }.sumOf { it.han }
        assertEquals("double wind should give 2 yakuhai han total", 2, yakuhaiHan)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Riichi + menzen tsumo interaction
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `riichi is detected from context flag`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx(isRiichi = true))
        assertTrue("riichi should be detected", result.yakuList.any { it.yaku == Yaku.RIICHI })
    }

    @Test
    fun `menzen tsumo — closed tsumo win gives yaku`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = true), ctx())
        assertTrue("menzen tsumo should be detected", result.yakuList.any { it.yaku == Yaku.MENZEN_TSUMO })
    }

    @Test
    fun `riichi and menzen tsumo both detected on tsumo win`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = true), ctx(isRiichi = true))
        assertTrue("riichi should be present", result.yakuList.any { it.yaku == Yaku.RIICHI })
        assertTrue("menzen tsumo should be present", result.yakuList.any { it.yaku == Yaku.MENZEN_TSUMO })
        assertEquals("total han = tanyao(1) + menzen_tsumo(1) + riichi(1) = 3", 3, result.totalHan)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Akadora counting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `akadora counted in dora total`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5, aka = true),  // red five
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        // tanyao = 1 han, akadora = 1 → totalHan = 2
        assertTrue("akadora should add to dora count", result.doraCount >= 1)
        assertEquals(2, result.totalHan)
    }

    @Test
    fun `no akadora means zero aka-dora`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = false), ctx())
        assertEquals(1, result.totalHan) // only tanyao
    }

    @Test
    fun `normal dora indicator maps to next tile`() {
        // Dora indicator = 4m → actual dora = 5m
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),  // 5m is dora
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(doraIndicators = listOf(man(4))),  // indicator 4m → dora 5m
        )
        // tanyao=1 + 1 dora (5m) = 2 han
        assertEquals(2, result.totalHan)
        assertTrue(result.doraCount >= 1)
    }

    @Test
    fun `dora indicator 9 wraps to 1 of same suit`() {
        // Indicator 9m → dora 1m
        val closed = listOf(
            man(1), man(2), man(3),  // 1m is dora
            man(4), man(5), man(6),
            pin(1), pin(2), pin(3),
            pin(4), pin(5), pin(6),
            sou(5),
        )
        // No tanyao (has terminals), but has dora (1m). Need a yaku.
        // Add riichi for a yaku
        val result = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(isRiichi = true, doraIndicators = listOf(man(9))),
        )
        // riichi=1 + 1 dora (1m) = 2 han
        assertEquals(2, result.totalHan)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Iipeiko
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `iipeiko — two identical sequences gives 1 han`() {
        // 1m2m3m 1m2m3m 5p6p7p + pair 9p9p, winning on 9p (tanki)
        val closed = listOf(
            man(1), man(2), man(3),
            man(1), man(2), man(3),
            pin(5), pin(6), pin(7),
            pin(9), pin(9),
            sou(3), sou(3),
        )
        val result = engine.score(closedHand(closed, sou(3), isTsumo = false), ctx(isRiichi = true))
        assertTrue("iipeiko should be detected", result.yakuList.any { it.yaku == Yaku.IIPEIKO })
        assertEquals(1, result.yakuList.first { it.yaku == Yaku.IIPEIKO }.han)
    }

    @Test
    fun `iipeiko does not apply to open hand`() {
        val openMeld = Meld(
            type = MeldType.CHI,
            tiles = listOf(man(7), man(8), man(9)),
            calledTile = man(9),
            calledFrom = 2,
        )
        // 1 open meld (3 tiles) → 10 closed + 1 winning = 14 total
        val closed = listOf(
            man(1), man(2), man(3),
            man(1), man(2), man(3),
            pin(5), pin(6), pin(7),
            sou(3),  // pair partner; winning tile is the other sou(3)
        )
        val hand = Hand(closedTiles = closed, openMelds = listOf(openMeld), winningTile = sou(3), isTsumo = false)
        val result = engine.score(hand, ctx())
        assertFalse("iipeiko should not apply to open hand", result.yakuList.any { it.yaku == Yaku.IIPEIKO })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Score outputs (han/fu/points)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `1 han 30 fu non-dealer ron = 1000 points`() {
        // Open tanyao (1 han), ryanmen wait, simple pair → no pinfu (open hand)
        // Fu: 30 base + 0 (ryanmen) + 0 (simple pair) + 0 (open sequences) = 30
        // basic = 30 × 8 = 240, non-dealer ron = 240×4 = 960 → 1000
        //
        // Open CHI: 2p3p4p; closed pool: 2m3m4m 5m6m7m 5p6p7p 8s8s
        // Decomp: [chi 2p3p4p] | 2m3m4m | 5m6m7m | 5p6p7p | pair 8s8s
        // Wait: winning 7p is pos-2 of 5p6p7p (t1=5p≠1) → RYANMEN → 0 wait fu
        val openChi = Meld(
            type = MeldType.CHI,
            tiles = listOf(pin(2), pin(3), pin(4)),
            calledTile = pin(4),
            calledFrom = 2,
        )
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(5), pin(6),     // 2-tile skeleton, winning 7p completes 5p6p7p
            sou(8), sou(8),     // simple pair
        )
        val hand = Hand(closedTiles = closed, openMelds = listOf(openChi), winningTile = pin(7), isTsumo = false)
        val result = engine.score(hand, ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH))
        assertTrue("tanyao should be detected", result.yakuList.any { it.yaku == Yaku.TANYAO })
        assertEquals(30, result.fu)
        assertEquals(1000, result.ronPayment)
        assertEquals(1000, result.pointsWon)
    }

    @Test
    fun `dealer ron is higher than non-dealer ron for same hand`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val nonDealerResult = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH),
        )
        val dealerResult = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.EAST),
        )
        assertNotNull(nonDealerResult.ronPayment)
        assertNotNull(dealerResult.ronPayment)
        assertTrue("dealer pays more than non-dealer", dealerResult.ronPayment!! > nonDealerResult.ronPayment!!)
    }

    @Test
    fun `tsumo has tsumoPayments set and ronPayment null`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val result = engine.score(closedHand(closed, sou(5), isTsumo = true), ctx())
        assertNull("tsumo should have no ronPayment", result.ronPayment)
        assertNotNull("tsumo should have tsumoPayments", result.tsumoPayments)
    }

    @Test
    fun `chiitoitsu 2 han 25 fu non-dealer ron = 1600 points`() {
        // 2 han 25 fu basic=25*16=400, non-dealer ron=400*4=1600
        val closed = listOf(
            man(1), man(1), man(3), man(3), man(5), man(5),
            man(7), man(7), pin(2), pin(2), pin(4), pin(4),
            pin(6),
        )
        val result = engine.score(
            closedHand(closed, pin(6), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH),
        )
        assertEquals(2, result.totalHan)
        assertEquals(25, result.fu)
        assertEquals(1600, result.ronPayment)
    }

    @Test
    fun `5 han is mangan regardless of fu`() {
        // riichi(1) + menzen_tsumo(1) + tanyao(1) + 2 akadora = 5 han
        val closed = listOf(
            man(2), man(3), man(4),
            man(5, aka = true), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5, aka = true),
        )
        val result = engine.score(
            closedHand(closed, sou(5), isTsumo = true),
            ctx(isRiichi = true),
        )
        assertEquals(5, result.totalHan)
        assertEquals(LimitHand.MANGAN, result.limitHand)
    }

    @Test
    fun `honba adds 100 to ron payment`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val withoutHonba = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH, honba = 0),
        )
        val withHonba = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH, honba = 2),
        )
        assertEquals(
            "2 honba should add 200 to ron payment",
            (withoutHonba.ronPayment ?: 0) + 200,
            withHonba.ronPayment,
        )
    }

    @Test
    fun `riichi sticks on table go to winner`() {
        val closed = listOf(
            man(2), man(3), man(4),
            man(5), man(6), man(7),
            pin(3), pin(4), pin(5),
            pin(6), pin(7), pin(8),
            sou(5),
        )
        val withSticks = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH, riichiSticks = 3),
        )
        val withoutSticks = engine.score(
            closedHand(closed, sou(5), isTsumo = false),
            ctx(roundWind = Wind.EAST, seatWind = Wind.SOUTH, riichiSticks = 0),
        )
        assertEquals("3 riichi sticks = +3000 to winner", withoutSticks.pointsWon + 3000, withSticks.pointsWon)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Fu calculation spot checks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `closed triplet of simple adds 4 fu`() {
        // base 30 + closed triplet of 5m(4 fu) + tanki pair of 9p = 30+4+2 = 36 → 40 fu
        val closed = listOf(
            man(2), man(3), man(4),
            pin(1), pin(2), pin(3),
            pin(4), pin(5), pin(6),
            sou(5), sou(5), sou(5),
            pin(9),
        )
        val result = engine.score(closedHand(closed, pin(9), isTsumo = false), ctx(isRiichi = true))
        assertEquals(40, result.fu)
    }

    @Test
    fun `yakuhai pair adds 2 fu`() {
        // riichi, all sequences, haku pair (2 fu) + ryanmen (0) → 30+2=32 → 40 fu
        val closed = listOf(
            man(1), man(2), man(3),
            man(4), man(5), man(6),
            pin(1), pin(2), pin(3),
            pin(4), pin(5), pin(6),
            haku(),
        )
        val result = engine.score(closedHand(closed, haku(), isTsumo = false), ctx(isRiichi = true))
        // tanki wait (haku pair) + yakuhai pair: 30 + 2(pair) + 2(tanki wait) = 34 → 40
        assertEquals(40, result.fu)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. No-yaku hand
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `hand with no yaku returns empty yakuList and zero points`() {
        // Open hand with no valid yaku (chi only, but no tanyao since has terminal)
        val openMeld = Meld(
            type = MeldType.CHI,
            tiles = listOf(man(1), man(2), man(3)),
            calledTile = man(3),
            calledFrom = 2,
        )
        // 1 open meld (3 tiles) → 10 closed + 1 winning = 14 total
        val closed = listOf(
            man(4), man(5), man(6),
            man(7), man(8), man(9),
            pin(1), pin(2), pin(3),
            pin(9),  // pair partner; winning is the other pin(9)
        )
        val hand = Hand(closedTiles = closed, openMelds = listOf(openMeld), winningTile = pin(9), isTsumo = false)
        val result = engine.score(hand, ctx())
        assertTrue("no yaku hand should have empty yakuList", result.yakuList.isEmpty())
        assertEquals(0, result.pointsWon)
    }
}
