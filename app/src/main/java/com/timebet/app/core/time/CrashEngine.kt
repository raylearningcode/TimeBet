package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG
import kotlin.math.E
import kotlin.math.pow
import kotlin.math.round

/**
 * Crash casino game engine.
 *
 * PRD Section 14: Multiplier rises from 1.00x, user cashes out before crash.
 *
 * Crash point is generated using a heavy-tailed distribution.
 * Most games crash under 2x — high multipliers are rare and thrilling.
 *
 * House edge: 8% — the expected multiplier is below fair value.
 * Instant crash: 5% chance (1.00x) to create tension.
 * Growth rate: 12% per second — fast, forces quick decisions.
 */
class CrashEngine {

    companion object {
        const val MIN_MULTIPLIER = 1.0
        const val HOUSE_EDGE = 0.08 // 8% — makes the game risky
        const val INSTANT_CRASH_PROBABILITY = 0.05 // 5% chance of instant crash
        const val GROWTH_RATE = 0.12 // 12% per second — fast acceleration
    }

    /**
     * Generate a crash point multiplier.
     *
     * Uses the standard crash game formula with increased house edge:
     * 1. 5% chance of instant crash (1.00x)
     * 2. Otherwise: crash_point = max(1.01, 0.92 / h)
     *    where h is a uniform random value in (0, 1)
     *
     * With 8% house edge, most games crash between 1.0x and 3.0x.
     * High multipliers (5x+) are very rare.
     */
    fun generateCrashPoint(): Double {
        // Instant crash check — 5% of games crash immediately
        if (CryptoRNG.nextDouble() < INSTANT_CRASH_PROBABILITY) {
            return 1.00
        }

        val h = CryptoRNG.nextDouble()
        // Prevent division by near-zero (which would give absurd multipliers)
        val safe = h.coerceIn(0.015, 0.999)

        // Heavy-tailed crash formula with 8% house edge
        // Expected value ≈ 0.92 / E[h] ≈ 0.92 / 0.5 ≈ 1.84x (before adjustment)
        // With house edge applied, expected multiplier ≈ 1.69x
        val rawMultiplier = (1.0 - HOUSE_EDGE) / safe

        // Floor to 2 decimal places for display cleanliness
        val finalMultiplier = round(rawMultiplier * 100.0) / 100.0

        return maxOf(MIN_MULTIPLIER, finalMultiplier)
    }

    /**
     * Calculate current multiplier at a given elapsed time.
     * The multiplier grows exponentially at GROWTH_RATE per second.
     *
     * Growth is capped at the target crash point.
     */
    fun calculateMultiplierAtTime(elapsedMs: Long, targetCrash: Double): Double {
        val t = elapsedMs / 1000.0 // seconds
        val currentMultiplier = E.pow(t * GROWTH_RATE) // fast 12% growth per second

        return round(minOf(currentMultiplier, targetCrash) * 100.0) / 100.0
    }

    /**
     * Calculate payout from cashing out at a given multiplier.
     * Payout = stake × multiplier (includes original stake).
     * Profit = payout - stake.
     */
    fun calculatePayout(stakeSeconds: Long, cashOutMultiplier: Double): Long {
        val payout = stakeSeconds * cashOutMultiplier
        val profit = payout - stakeSeconds
        return profit.coerceAtLeast(0.0).toLong()
    }
}

data class CrashGameState(
    val stakeSeconds: Long,
    val crashPoint: Double,
    val currentMultiplier: Double = 1.0,
    val hasCrashed: Boolean = false,
    val hasCashedOut: Boolean = false,
    val cashOutMultiplier: Double? = null,
    val profitSeconds: Long = 0
) {
    val isActive: Boolean get() = !hasCrashed && !hasCashedOut
}
