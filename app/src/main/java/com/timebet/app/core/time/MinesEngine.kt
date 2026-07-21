package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG

/**
 * Mines casino game engine.
 *
 * PRD Section 11: 5x5 grid, 1-24 mines, dynamic multiplier.
 *
 * Multiplier formula:
 * For each safe tile revealed, the multiplier increases based on the
 * probability of having survived that many reveals.
 *
 * multiplier(n) = base_multiplier * (total_tiles / safe_tiles_at_step_n)
 * where the house edge is baked into the multiplier calculation.
 *
 * PRD Section 11.4: Multiplier calculated dynamically based on:
 * - Total tiles (25)
 * - Mine count
 * - Number of safe tiles revealed
 * - Probability of surviving the selected number of reveals
 * - Configured house advantage (here: ~3%)
 */
class MinesEngine {

    companion object {
        const val GRID_SIZE = 5
        const val TOTAL_TILES = GRID_SIZE * GRID_SIZE // 25
        const val HOUSE_EDGE = 0.03 // 3% house advantage
        const val MAX_MINES = 24
        const val MIN_MINES = 1
    }

    /**
     * Generate mine positions for a new round.
     * Returns the list of mine indices (0-24).
     */
    fun generateMinePositions(mineCount: Int): Set<Int> {
        require(mineCount in MIN_MINES..MAX_MINES) {
            "Mine count must be between $MIN_MINES and $MAX_MINES"
        }

        val positions = (0 until TOTAL_TILES).toMutableList()
        CryptoRNG.shuffle(positions)
        return positions.take(mineCount).toSet()
    }

    /**
     * Calculate the current multiplier after revealing n safe tiles.
     *
     * The multiplier represents the cumulative probability of surviving
     * n reveals, adjusted for house edge.
     *
     * After revealing n safe tiles:
     * multiplier(n) = product of (total_tiles - i) / (safe_tiles_remaining_at_step_i) * (1 - house_edge)
     *
     * Simplified formula using fair odds with house edge applied:
     * fair_multiplier(n) = C(total, n) / C(safe, n)  [combinatorial]
     * actual_multiplier(n) = fair_multiplier(n) * (1 - house_edge)^n
     */
    fun calculateMultiplier(mineCount: Int, safeRevealed: Int): Double {
        require(mineCount in MIN_MINES..MAX_MINES)
        require(safeRevealed >= 0)

        val safeTiles = TOTAL_TILES - mineCount

        if (safeRevealed == 0) return 1.0
        if (safeRevealed > safeTiles) return 1.0

        var fairMultiplier = 1.0
        for (i in 0 until safeRevealed) {
            // Probability of surviving this step: (safeTiles - i) / (TOTAL_TILES - i)
            val survivalProb = (safeTiles - i).toDouble() / (TOTAL_TILES - i).toDouble()
            // Fair payout for this step = 1 / survivalProb
            fairMultiplier /= survivalProb
        }

        // Apply house edge per reveal
        val adjustedMultiplier = fairMultiplier * Math.pow(1.0 - HOUSE_EDGE, safeRevealed.toDouble())

        // Round to 2 decimal places
        return Math.round(adjustedMultiplier * 100.0) / 100.0
    }

    /**
     * Calculate the cash-out amount for a given stake and number of safe reveals.
     */
    fun calculatePayout(stakeSeconds: Long, mineCount: Int, safeRevealed: Int): Long {
        val multiplier = calculateMultiplier(mineCount, safeRevealed)
        return (stakeSeconds * multiplier).toLong()
    }

    /**
     * Check if a tile is a mine.
     */
    fun isMine(tileIndex: Int, minePositions: Set<Int>): Boolean {
        return tileIndex in minePositions
    }

    /**
     * Get risk level description based on mine count.
     */
    fun getRiskLevel(mineCount: Int): RiskLevel {
        return when {
            mineCount <= 3 -> RiskLevel.LOW
            mineCount <= 8 -> RiskLevel.MEDIUM
            mineCount <= 15 -> RiskLevel.HIGH
            else -> RiskLevel.EXTREME
        }
    }
}

enum class RiskLevel(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    EXTREME("Extreme")
}

data class MinesGameState(
    val mineCount: Int,
    val minePositions: Set<Int>,
    val revealedTiles: Set<Int> = emptySet(),
    val currentMultiplier: Double = 1.0,
    val stakeSeconds: Long,
    val isGameOver: Boolean = false,
    val isWin: Boolean = false,
    val safeTilesRemaining: Int = MinesEngine.TOTAL_TILES - mineCount
) {
    val safeTiles: Int get() = MinesEngine.TOTAL_TILES - mineCount
}
