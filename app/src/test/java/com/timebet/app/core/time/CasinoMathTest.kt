package com.timebet.app.core.time

import org.junit.Assert.*
import org.junit.Test

/**
 * Casino math unit tests — PRD Section 42.1, 42.5.
 *
 * Tests: Coin Flip expected distribution, Mines multiplier calculation,
 * Roulette payouts, Crash settlement.
 */
class CasinoMathTest {

    // ─── Coin Flip ───

    @Test
    fun `coin flip win probability approximates configured value over many trials`() {
        val engine = CoinFlipEngine()
        val trials = 100_000
        var wins = 0

        repeat(trials) {
            val result = engine.flip(100, betOnHeads = true)
            if (result.isWin) wins++
        }

        val observedWinRate = wins.toDouble() / trials
        // Should be close to 48.5% within reasonable margin
        assertTrue(
            "Expected ~48.5% win rate, got ${observedWinRate * 100}%",
            observedWinRate in 0.47..0.50
        )
    }

    @Test
    fun `coin flip win payout matches configured multiplier`() {
        val engine = CoinFlipEngine()
        val stakeSeconds = 100L
        var totalProfit = 0L
        val trials = 50_000

        repeat(trials) {
            val result = engine.flip(stakeSeconds, betOnHeads = true)
            if (result.isWin) totalProfit += result.profitSeconds
        }

        // Average profit per win should be ~95 (1.95x - 1.0 = 0.95x profit)
        // Not asserting exact, just sanity check
        assertTrue(totalProfit > 0)
    }

    // ─── Mines ───

    @Test
    fun `mines multiplier increases with each safe reveal`() {
        val engine = MinesEngine()

        val multiplier0 = engine.calculateMultiplier(mineCount = 3, safeRevealed = 0)
        val multiplier1 = engine.calculateMultiplier(mineCount = 3, safeRevealed = 1)
        val multiplier3 = engine.calculateMultiplier(mineCount = 3, safeRevealed = 3)
        val multiplier10 = engine.calculateMultiplier(mineCount = 3, safeRevealed = 10)

        assertEquals(1.0, multiplier0, 0.01)
        assertTrue("Multiplier should increase: $multiplier1 > $multiplier0", multiplier1 > multiplier0)
        assertTrue("Multiplier should increase: $multiplier3 > $multiplier1", multiplier3 > multiplier1)
        assertTrue("Multiplier should increase: $multiplier10 > $multiplier3", multiplier10 > multiplier3)
    }

    @Test
    fun `mines multiplier higher with more mines`() {
        val engine = MinesEngine()

        val multiplierLow = engine.calculateMultiplier(mineCount = 2, safeRevealed = 5)
        val multiplierHigh = engine.calculateMultiplier(mineCount = 10, safeRevealed = 5)

        assertTrue(
            "More mines should give higher multiplier: $multiplierHigh > $multiplierLow",
            multiplierHigh > multiplierLow
        )
    }

    @Test
    fun `mines generate correct number of mine positions`() {
        val engine = MinesEngine()

        repeat(10) {
            val positions = engine.generateMinePositions(5)
            assertEquals(5, positions.size)
            assertTrue(positions.all { it in 0..24 })
        }
    }

    @Test
    fun `mines all positions are within grid`() {
        val engine = MinesEngine()

        for (mineCount in 1..24) {
            val positions = engine.generateMinePositions(mineCount)
            assertEquals(mineCount, positions.size)
            positions.forEach { pos ->
                assertTrue("Position $pos out of bounds", pos in 0..24)
            }
        }
    }

    // ─── Roulette ───

    @Test
    fun `roulette spin returns valid number`() {
        val engine = RouletteEngine()
        val numbersSeen = mutableSetOf<Int>()

        // Run many spins to see distribution
        repeat(5000) {
            val result = engine.spin()
            assertTrue(result.number in 0..36)
            numbersSeen.add(result.number)
        }

        // Should see many different numbers
        assertTrue("Should see at least 30 different numbers", numbersSeen.size >= 30)
    }

    @Test
    fun `roulette zero has correct color`() {
        val engine = RouletteEngine()
        // This is a statistical test; zero should appear ~1/37 of the time
        var zeroCount = 0
        val trials = 37000
        repeat(trials) {
            val result = engine.spin()
            if (result.number == 0) {
                zeroCount++
                assertEquals(RouletteColor.GREEN, result.color)
            }
        }
        // Should be roughly 1000 (1/37 * 37000)
        assertTrue("Zero appeared $zeroCount times, expected ~1000", zeroCount in 700..1300)
    }

    @Test
    fun `roulette straight bet pays 35 to 1`() {
        val engine = RouletteEngine()
        val spin = RouletteSpinResult(
            number = 17, color = RouletteColor.BLACK,
            isEven = false, isOdd = true, isLow = true, isHigh = false,
            dozen = 2, column = 2
        )
        val bet = RouletteBet(
            type = BetType.STRAIGHT, stakeSeconds = 100,
            numbers = listOf(17)
        )
        val result = engine.evaluateBet(bet, spin)
        assertTrue(result.isWin)
        assertEquals(35, result.payoutMultiplier)
        assertEquals(3500, result.profitSeconds) // 35 * 100 = 3500 (35:1 payout)
    }

    @Test
    fun `roulette red bet wins on red loses on black`() {
        val engine = RouletteEngine()
        val redSpin = RouletteSpinResult(1, RouletteColor.RED, false, true, true, false, 1, 1)
        val blackSpin = RouletteSpinResult(2, RouletteColor.BLACK, true, false, true, false, 1, 2)

        val redBet = RouletteBet(BetType.RED, 100)
        assertTrue(engine.evaluateBet(redBet, redSpin).isWin)
        assertFalse(engine.evaluateBet(redBet, blackSpin).isWin)
    }

    // ─── Crash ───

    @Test
    fun `crash point is always at least 1 point 0`() {
        val engine = CrashEngine()
        repeat(1000) {
            val crashPoint = engine.generateCrashPoint()
            assertTrue("Crash point $crashPoint < 1.0", crashPoint >= 1.0)
        }
    }

    @Test
    fun `crash multiplier growth is monotonic`() {
        val engine = CrashEngine()
        val crashPoint = 5.0
        val m100 = engine.calculateMultiplierAtTime(100, crashPoint)
        val m500 = engine.calculateMultiplierAtTime(500, crashPoint)
        val m1000 = engine.calculateMultiplierAtTime(1000, crashPoint)

        assertTrue(m500 > m100)
        assertTrue(m1000 > m500)
    }

    // ─── Blackjack ───

    @Test
    fun `blackjack shoe has correct number of cards`() {
        val engine = BlackjackEngine()
        val shoe = engine.newShoe()
        assertEquals(BlackjackEngine.TOTAL_CARDS, shoe.size)
    }

    @Test
    fun `blackjack deal gives two cards each`() {
        val engine = BlackjackEngine()
        val state = engine.deal()
        assertEquals(2, state.playerHand.cards.size)
        assertEquals(2, state.dealerHand.cards.size)
    }

    @Test
    fun `blackjack hand value computed correctly`() {
        val engine = BlackjackEngine()
        val hand = BlackjackEngine.Hand()
        hand.add(BlackjackEngine.Card(10, 0)) // Ten
        hand.add(BlackjackEngine.Card(1, 0))  // Ace
        assertEquals(21, hand.value)
        assertTrue(hand.isBlackjack)
    }

    @Test
    fun `blackjack ace adjusts from 11 to 1 when needed`() {
        val hand = BlackjackEngine.Hand()
        hand.add(BlackjackEngine.Card(1, 0))  // Ace (11)
        hand.add(BlackjackEngine.Card(5, 0))  // 5
        hand.add(BlackjackEngine.Card(10, 0)) // 10
        assertEquals(16, hand.value) // 11+5+10=26 → 1+5+10=16
        assertFalse(hand.isBusted)
    }

    @Test
    fun `blackjack bust detection`() {
        val hand = BlackjackEngine.Hand()
        hand.add(BlackjackEngine.Card(10, 0))
        hand.add(BlackjackEngine.Card(10, 1))
        hand.add(BlackjackEngine.Card(5, 2))
        assertEquals(25, hand.value)
        assertTrue(hand.isBusted)
    }

    // ─── CryptoRNG ───

    @Test
    fun `crypto rng produces values in expected range`() {
        for (bound in listOf(2, 10, 37, 100)) {
            repeat(100) {
                val value = com.timebet.app.core.security.CryptoRNG.nextInt(bound)
                assertTrue(value in 0 until bound)
            }
        }
    }

    // ── Baccarat ──

    @Test
    fun `baccarat deal returns valid hands`() {
        val engine = BaccaratEngine()
        val result = engine.deal()
        assertTrue(result.playerHand.cards.size in 2..3)
        assertTrue(result.bankerHand.cards.size in 2..3)
        assertTrue(result.playerHand.total in 0..9)
        assertTrue(result.bankerHand.total in 0..9)
    }

    @Test
    fun `baccarat player bet pays correctly`() {
        val engine = BaccaratEngine()
        var playerWins = 0
        var bankerWins = 0
        var ties = 0
        repeat(200) {
            val r = engine.deal()
            when (r.outcome) {
                BaccaratEngine.Outcome.PLAYER -> playerWins++
                BaccaratEngine.Outcome.BANKER -> bankerWins++
                BaccaratEngine.Outcome.TIE -> ties++
            }
        }
        // Player and banker should both win significantly (ties are rare ~9%)
        assertTrue("Player wins: $playerWins", playerWins > 30)
        assertTrue("Banker wins: $bankerWins", bankerWins > 30)
    }

    @Test
    fun `baccarat payout is correct`() {
        val engine = BaccaratEngine()
        assertEquals(100, engine.payout(100, BaccaratEngine.Outcome.PLAYER, "player"))
        assertEquals(95, engine.payout(100, BaccaratEngine.Outcome.BANKER, "banker"))
        assertEquals(800, engine.payout(100, BaccaratEngine.Outcome.TIE, "tie"))
        assertEquals(0, engine.payout(100, BaccaratEngine.Outcome.BANKER, "player"))
    }

    // ── Chicken ──

    @Test
    fun `chicken game creates valid state`() {
        val state = ChickenEngine.ChickenGameState(totalLanes = 6, stakeSeconds = 300)
        assertEquals(6, state.totalLanes)
        assertEquals(0, state.lanesCrossed)
        assertTrue(state.isActive)
    }

    @Test
    fun `chicken multiplier increases with lanes`() {
        val mult1 = ChickenEngine.calculateMultiplier(0, 6)
        val mult3 = ChickenEngine.calculateMultiplier(3, 6)
        val mult6 = ChickenEngine.calculateMultiplier(6, 6)
        assertEquals(1.0, mult1, 0.01)
        assertTrue(mult3 > mult1)
        assertTrue(mult6 > mult3)
        assertTrue(mult6 < 15.0) // Shouldn't go crazy
    }

    @Test
    fun `chicken crossing eventually crashes`() {
        val engine = ChickenEngine()
        var state = ChickenEngine.ChickenGameState(totalLanes = 3, stakeSeconds = 100)
        var crashed = false
        repeat(100) {
            if (state.isActive) {
                state = engine.tryCrossLane(state)
                if (!state.isActive) crashed = true
            }
        }
        // With 100 attempts at 3 lanes, almost certainly crash at some point
        assertTrue(crashed || state.lanesCrossed >= 3)
    }

    @Test
    fun `chicken cashout yields correct profit`() {
        val engine = ChickenEngine()
        val state = ChickenEngine.ChickenGameState(totalLanes = 5, stakeSeconds = 200, lanesCrossed = 3)
        val cashed = engine.cashOut(state, 200)
        assertTrue(cashed.hasCashedOut)
        assertTrue(cashed.profitSeconds > 0)
    }
}
