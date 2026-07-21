package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG

/**
 * Coin Flip casino game engine.
 *
 * PRD Section 10: Double-or-lose game with a house advantage.
 *
 * Fair-looking 50/50 game with 2x payout, but the true win probability
 * is 49% — giving the house a 2% edge over time.
 *
 * RTP = 49% × 2.0 = 98%
 */
class CoinFlipEngine {

    companion object {
        /** Win probability: slightly below 50% for house advantage */
        const val WIN_PROBABILITY = 0.49

        /** Payout multiplier: 2x stake on win (true double-or-nothing) */
        const val PAYOUT_MULTIPLIER = 2.0

        /** Expected Return to Player (RTP) = 49% × 2.0 = 98% */
        const val RTP = WIN_PROBABILITY * PAYOUT_MULTIPLIER
    }

    /**
     * Resolve a coin flip.
     *
     * The coin is fair (50/50 heads/tails), but the win condition
     * applies the house edge: user only wins 49% of the time.
     *
     * @param stakeSeconds The wager in seconds
     * @param betOnHeads User's choice (true = heads, false = tails)
     * @return The result of the flip
     */
    fun flip(stakeSeconds: Long, betOnHeads: Boolean): CoinFlipResult {
        require(stakeSeconds > 0) { "Stake must be positive" }

        // Fair coin toss — 50/50 heads or tails
        val coinIsHeads = CryptoRNG.nextBoolean(0.5)

        // User guessed correctly AND beats the house edge
        val guessedCorrectly = coinIsHeads == betOnHeads
        val beatsHouseEdge = CryptoRNG.nextBoolean(WIN_PROBABILITY / 0.5)
        val finalWin = guessedCorrectly && beatsHouseEdge

        // Payout: 2x means profit = stake (get your stake back + equal profit)
        val profit = if (finalWin) stakeSeconds else 0L

        return CoinFlipResult(
            isWin = finalWin,
            coinIsHeads = coinIsHeads,
            betWasHeads = betOnHeads,
            stakeSeconds = stakeSeconds,
            profitSeconds = profit,
            lossSeconds = if (finalWin) 0L else stakeSeconds,
            payoutMultiplier = if (finalWin) PAYOUT_MULTIPLIER else 0.0
        )
    }
}

data class CoinFlipResult(
    val isWin: Boolean,
    val coinIsHeads: Boolean,
    val betWasHeads: Boolean,
    val stakeSeconds: Long,
    val profitSeconds: Long,
    val lossSeconds: Long,
    val payoutMultiplier: Double
)
