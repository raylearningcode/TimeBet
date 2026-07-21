package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG

/**
 * European single-zero Roulette engine.
 *
 * PRD Section 12: European single-zero, numbers 0-36.
 *
 * Natural house edge = 1/37 ≈ 2.7% (single zero).
 * Payouts follow standard European roulette rules.
 * All 37 numbers have equal probability (1/37).
 */
class RouletteEngine {

    companion object {
        const val TOTAL_NUMBERS = 37 // 0 to 36
        const val ZERO = 0
        const val HOUSE_EDGE = 1.0 / TOTAL_NUMBERS // ≈ 2.7%
    }

    /**
     * The standard European roulette wheel numbers arranged in order.
     */
    val wheelOrder = listOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
        10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )

    val redNumbers = setOf(
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
    )
    val blackNumbers = setOf(
        2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35
    )

    /**
     * Spin the wheel and return the result.
     */
    fun spin(): RouletteSpinResult {
        val number = CryptoRNG.nextInt(TOTAL_NUMBERS)
        val color = when {
            number == 0 -> RouletteColor.GREEN
            number in redNumbers -> RouletteColor.RED
            else -> RouletteColor.BLACK
        }
        return RouletteSpinResult(
            number = number,
            color = color,
            isEven = number != 0 && number % 2 == 0,
            isOdd = number % 2 == 1,
            isLow = number in 1..18,
            isHigh = number in 19..36,
            dozen = when {
                number in 1..12 -> 1
                number in 13..24 -> 2
                number in 25..36 -> 3
                else -> 0
            },
            column = when {
                number == 0 -> 0
                number % 3 == 1 -> 1
                number % 3 == 2 -> 2
                else -> 3 // number % 3 == 0
            }
        )
    }

    /**
     * Evaluate a bet against the spin result.
     * Returns the payout multiplier (0 for loss, and the standard payout for win).
     *
     * Standard European roulette payouts:
     * - Straight up (single number): 35:1
     * - Split (2 numbers): 17:1
     * - Street (3 numbers): 11:1
     * - Corner (4 numbers): 8:1
     * - Line (6 numbers): 5:1
     * - Dozen/Column (12 numbers): 2:1
     * - Even-money (18 numbers): 1:1
     */
    fun evaluateBet(bet: RouletteBet, spin: RouletteSpinResult): RouletteBetResult {
        val won = when (bet.type) {
            BetType.STRAIGHT -> spin.number == bet.numbers.firstOrNull()
            BetType.RED -> spin.color == RouletteColor.RED
            BetType.BLACK -> spin.color == RouletteColor.BLACK
            BetType.ODD -> spin.isOdd
            BetType.EVEN -> spin.isEven
            BetType.LOW -> spin.isLow
            BetType.HIGH -> spin.isHigh
            BetType.DOZEN -> spin.dozen == bet.dozens.firstOrNull()
            BetType.COLUMN -> spin.column == bet.columns.firstOrNull()
        }

        val payoutMultiplier = if (won) bet.type.payout else 0
        val profit = if (won) {
            (bet.stakeSeconds.toLong() * payoutMultiplier)
        } else {
            0L
        }

        return RouletteBetResult(
            bet = bet,
            spin = spin,
            isWin = won,
            payoutMultiplier = payoutMultiplier,
            profitSeconds = profit,
            lossSeconds = if (won) 0L else bet.stakeSeconds
        )
    }
}

enum class BetType(val payout: Int, val description: String) {
    STRAIGHT(35, "Single Number"),
    RED(1, "Red"),
    BLACK(1, "Black"),
    ODD(1, "Odd"),
    EVEN(1, "Even"),
    LOW(1, "1-18"),
    HIGH(1, "19-36"),
    DOZEN(2, "Dozen"),
    COLUMN(2, "Column")
}

data class RouletteBet(
    val type: BetType,
    val stakeSeconds: Long,
    val numbers: List<Int> = emptyList(),
    val dozens: List<Int> = emptyList(),
    val columns: List<Int> = emptyList()
)

data class RouletteSpinResult(
    val number: Int,
    val color: RouletteColor,
    val isEven: Boolean,
    val isOdd: Boolean,
    val isLow: Boolean,
    val isHigh: Boolean,
    val dozen: Int,
    val column: Int
)

data class RouletteBetResult(
    val bet: RouletteBet,
    val spin: RouletteSpinResult,
    val isWin: Boolean,
    val payoutMultiplier: Int,
    val profitSeconds: Long,
    val lossSeconds: Long
)

enum class RouletteColor { RED, BLACK, GREEN }
