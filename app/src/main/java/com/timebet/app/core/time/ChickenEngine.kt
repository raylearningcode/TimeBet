package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG

/**
 * Chicken (Crossing Road) game engine.
 *
 * Player chooses difficulty (4-10 lanes). Each lane has a probability of a car.
 * Chicken crosses one lane at a time. Cash out after each successful crossing.
 * Multiplier increases exponentially with lanes crossed.
 * Crash = chicken hit by car.
 *
 * House edge: ~5%
 */
class ChickenEngine {

    data class ChickenGameState(
        val totalLanes: Int,
        val stakeSeconds: Long,
        val lanesCrossed: Int = 0,
        val isCrashed: Boolean = false,
        val hasCashedOut: Boolean = false,
        val cashOutMultiplier: Double = 1.0,
        val profitSeconds: Long = 0L
    ) {
        val isActive: Boolean get() = !isCrashed && !hasCashedOut && lanesCrossed < totalLanes
        val currentMultiplier: Double get() = calculateMultiplier(lanesCrossed, totalLanes)
    }

    companion object {
        const val MIN_LANES = 4
        const val MAX_LANES = 10
        const val HOUSE_EDGE = 0.05

        // Car probability per lane position (increases as you go further)
        // Lane 1 (closest): 15%, Lane 10 (farthest): 40%
        fun carProbability(lane: Int, totalLanes: Int): Double {
            val progress = lane.toDouble() / totalLanes
            return 0.12 + progress * 0.30
        }

        fun calculateMultiplier(lanesCrossed: Int, totalLanes: Int): Double {
            if (lanesCrossed == 0) return 1.0
            val progress = lanesCrossed.toDouble() / totalLanes
            // Exponential growth: 1.0 → ~10x at max lanes
            val rawMultiplier = 1.0 + (progress * progress * 9.0)
            // Apply house edge
            val adjusted = rawMultiplier * (1.0 - HOUSE_EDGE * progress)
            return String.format("%.2f", adjusted.coerceAtLeast(1.01)).toDouble()
        }
    }

    fun tryCrossLane(state: ChickenGameState): ChickenGameState {
        if (!state.isActive) return state

        val nextLane = state.lanesCrossed + 1
        val crashProb = carProbability(nextLane, state.totalLanes)
        val crashed = CryptoRNG.nextDouble() < crashProb

        return if (crashed) {
            state.copy(isCrashed = true, lanesCrossed = nextLane)
        } else {
            state.copy(lanesCrossed = nextLane)
        }
    }

    fun cashOut(state: ChickenGameState, stakeSeconds: Long): ChickenGameState {
        val multiplier = calculateMultiplier(state.lanesCrossed, state.totalLanes)
        val payout = (stakeSeconds * multiplier).toLong()
        val profit = (payout - stakeSeconds).coerceAtLeast(0)
        return state.copy(
            hasCashedOut = true,
            cashOutMultiplier = multiplier,
            profitSeconds = profit
        )
    }

    fun calculatePayout(stakeSeconds: Long, state: ChickenGameState): Long {
        val multiplier = calculateMultiplier(state.lanesCrossed, state.totalLanes)
        return (stakeSeconds * multiplier).toLong()
    }
}
