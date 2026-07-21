package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG

/**
 * Baccarat engine — standard casino rules, 8-deck shoe.
 *
 * House edges:
 *   Banker  ~1.06%  (pays 1.95x — 5% commission)
 *   Player  ~1.24%  (pays 2x)
 *   Tie     ~14.36% (pays 9x)
 */
class BaccaratEngine {

    companion object {
        const val DECKS = 8
        const val TOTAL_CARDS = DECKS * 52
        const val RESHUFFLE_AT = 52 // Reshuffle when fewer than 52 cards remain
    }

    private val shoe = mutableListOf<Card>()
    private var shoeIndex = 0

    init {
        buildShoe()
    }

    // ── Card model ──

    data class Card(val rank: Int, val suit: String) {
        val displayRank: String get() = when (rank) {
            1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"
            else -> rank.toString()
        }
        val value: Int get() = when {
            rank >= 10 -> 0 // 10, J, Q, K = 0
            else -> rank   // A=1, 2-9 = face value
        }
        val suitSymbol: String get() = when (suit) {
            "hearts" -> "♥"; "diamonds" -> "♦"; "clubs" -> "♣"; else -> "♠"
        }
    }

    data class Hand(val cards: List<Card>) {
        val total: Int get() = cards.sumOf { it.value } % 10
        val isNatural: Boolean get() = cards.size == 2 && total >= 8
    }

    enum class Outcome { PLAYER, BANKER, TIE }

    data class BaccaratResult(
        val playerHand: Hand,
        val bankerHand: Hand,
        val outcome: Outcome,
        val playerThird: Card? = null,
        val bankerThird: Card? = null
    )

    // ── Shoe ──

    private fun buildShoe() {
        shoe.clear()
        val suits = listOf("hearts", "diamonds", "clubs", "spades")
        repeat(DECKS) {
            for (suit in suits) {
                for (rank in 1..13) {
                    shoe.add(Card(rank, suit))
                }
            }
        }
        CryptoRNG.shuffle(shoe)
        shoeIndex = 0
    }

    fun dealCard(): Card {
        if (shoeIndex >= shoe.size - RESHUFFLE_AT) {
            buildShoe()
        }
        return shoe[shoeIndex++]
    }

    fun cardsRemaining(): Int = shoe.size - shoeIndex

    // ── Game logic ──

    fun deal(): BaccaratResult {
        val p1 = dealCard(); val b1 = dealCard()
        val p2 = dealCard(); val b2 = dealCard()

        val playerCards = mutableListOf(p1, p2)
        val bankerCards = mutableListOf(b1, b2)
        var playerThird: Card? = null
        var bankerThird: Card? = null

        val playerTotal = Hand(playerCards).total
        val bankerTotal = Hand(bankerCards).total

        // Natural check
        if (playerTotal >= 8 || bankerTotal >= 8) {
            return resolve(Hand(playerCards.toList()), Hand(bankerCards.toList()))
        }

        // Player third card rule
        if (playerTotal <= 5) {
            playerThird = dealCard()
            playerCards.add(playerThird)
        }

        // Banker third card rule (complex)
        val p3Val = playerThird?.value ?: -1
        val shouldBankerDraw = when {
            playerThird == null && bankerTotal <= 5 -> true
            playerThird == null -> false
            bankerTotal <= 2 -> true
            bankerTotal == 3 -> p3Val != 8
            bankerTotal == 4 -> p3Val in 2..7
            bankerTotal == 5 -> p3Val in 4..7
            bankerTotal == 6 -> p3Val in 6..7
            else -> false
        }

        if (shouldBankerDraw) {
            bankerThird = dealCard()
            bankerCards.add(bankerThird)
        }

        return resolve(Hand(playerCards.toList()), Hand(bankerCards.toList()), playerThird, bankerThird)
    }

    private fun resolve(
        player: Hand, banker: Hand,
        playerThird: Card? = null, bankerThird: Card? = null
    ): BaccaratResult {
        val outcome = when {
            player.total > banker.total -> Outcome.PLAYER
            banker.total > player.total -> Outcome.BANKER
            else -> Outcome.TIE
        }
        return BaccaratResult(player, banker, outcome, playerThird, bankerThird)
    }

    fun payout(stakeSeconds: Long, outcome: Outcome, betOn: String): Long {
        if (outcome == Outcome.TIE && betOn != "tie") return 0L // push on tie for P/B bets
        return when (betOn) {
            "player" -> if (outcome == Outcome.PLAYER) stakeSeconds else 0L
            "banker" -> if (outcome == Outcome.BANKER) (stakeSeconds * 0.95).toLong() else 0L
            "tie" -> if (outcome == Outcome.TIE) stakeSeconds * 8 else 0L
            else -> 0L
        }
    }

    fun isWin(outcome: Outcome, betOn: String): Boolean {
        return when (betOn) {
            "player" -> outcome == Outcome.PLAYER
            "banker" -> outcome == Outcome.BANKER
            "tie" -> outcome == Outcome.TIE
            else -> false
        }
    }
}
