package com.timebet.app.core.time

import com.timebet.app.core.security.CryptoRNG

/**
 * Blackjack casino game engine.
 *
 * PRD Section 13:
 * - One player hand vs dealer
 * - Hit, Stand, Double Down
 * - No insurance, no side bets, no splits in MVP
 * - Dealer stands on soft 17
 * - Blackjack pays 1.5:1 (standard 3:2)
 * - Standard deterministic shoe: 6 decks, reshuffled each hand
 *
 * House edge with perfect basic strategy: ~0.5% (6 decks, S17)
 */
class BlackjackEngine {

    companion object {
        const val DECKS = 6
        const val CARDS_PER_DECK = 52
        const val TOTAL_CARDS = DECKS * CARDS_PER_DECK
        const val BLACKJACK_PAYOUT = 1.5 // 3:2
        const val DEALER_STANDS_ON = 17 // soft 17
    }

    data class Card(val rank: Int, val suit: Int) {
        val displayRank: String get() = when (rank) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }

        val value: Int get() = when {
            rank == 1 -> 11 // Ace, adjusted later
            rank >= 10 -> 10
            else -> rank
        }

        val isAce: Boolean get() = rank == 1
    }

    data class Hand(val cards: MutableList<Card> = mutableListOf()) {
        fun add(card: Card) = cards.add(card)

        val value: Int get() {
            var total = cards.sumOf { it.value }
            var aces = cards.count { it.isAce }
            while (total > 21 && aces > 0) {
                total -= 10
                aces--
            }
            return total
        }

        val isBlackjack: Boolean get() = cards.size == 2 && value == 21
        val isBusted: Boolean get() = value > 21
        val isSoft: Boolean get() {
            var total = cards.sumOf { it.value }
            val aces = cards.count { it.isAce }
            return aces > 0 && total <= 21
        }
    }

    data class BlackjackState(
        val playerHand: Hand = Hand(),
        val dealerHand: Hand = Hand(),
        val shoe: List<Card> = emptyList(),
        val shoeIndex: Int = 0,
        val stakeSeconds: Long = 0,
        val isPlayerDone: Boolean = false,
        val isDealerDone: Boolean = false,
        val canDoubleDown: Boolean = true,
        val result: BlackjackResult? = null
    )

    data class BlackjackResult(
        val outcome: BlackjackOutcome,
        val playerValue: Int,
        val dealerValue: Int,
        val profitSeconds: Long,
        val lossSeconds: Long
    )

    enum class BlackjackOutcome {
        PLAYER_BLACKJACK,
        PLAYER_BUST,
        DEALER_BUST,
        PLAYER_WIN,
        DEALER_WIN,
        PUSH,
        PLAYER_WIN_BLACKJACK
    }

    /**
     * Create and shuffle a fresh 6-deck shoe.
     */
    fun newShoe(): List<Card> {
        val cards = mutableListOf<Card>()
        for (d in 0 until DECKS) {
            for (suit in 0..3) {
                for (rank in 1..13) {
                    cards.add(Card(rank, suit))
                }
            }
        }
        CryptoRNG.shuffle(cards)
        return cards
    }

    /**
     * Deal initial hands.
     */
    fun deal(): BlackjackState {
        val shoe = newShoe()
        val playerHand = Hand()
        val dealerHand = Hand()

        // Deal: player, dealer, player, dealer
        playerHand.add(shoe[0])
        dealerHand.add(shoe[1])
        playerHand.add(shoe[2])
        dealerHand.add(shoe[3])

        val state = BlackjackState(
            playerHand = playerHand,
            dealerHand = dealerHand,
            shoe = shoe,
            shoeIndex = 4,
            canDoubleDown = true
        )

        // Check for natural blackjacks
        if (playerHand.isBlackjack && dealerHand.isBlackjack) {
            return state.copy(
                isPlayerDone = true,
                isDealerDone = true,
                result = BlackjackResult(BlackjackOutcome.PUSH, 21, 21, 0, 0)
            )
        }
        if (playerHand.isBlackjack) {
            return state.copy(
                isPlayerDone = true,
                isDealerDone = true,
                result = BlackjackResult(
                    BlackjackOutcome.PLAYER_BLACKJACK,
                    21, dealerHand.value, 0, 0
                ) // profit calculated by caller
            )
        }
        if (dealerHand.isBlackjack) {
            return state.copy(
                isPlayerDone = true,
                isDealerDone = true,
                result = BlackjackResult(BlackjackOutcome.DEALER_WIN, playerHand.value, 21, 0, 0)
            )
        }

        return state
    }

    /**
     * Player hits.
     */
    fun hit(state: BlackjackState): BlackjackState {
        val newPlayer = Hand(state.playerHand.cards.toMutableList())
        val nextCard = state.shoe[state.shoeIndex]
        newPlayer.add(nextCard)
        val newIndex = state.shoeIndex + 1

        return if (newPlayer.isBusted) {
            state.copy(
                playerHand = newPlayer,
                shoeIndex = newIndex,
                isPlayerDone = true,
                isDealerDone = true,
                canDoubleDown = false,
                result = BlackjackResult(
                    BlackjackOutcome.PLAYER_BUST,
                    newPlayer.value, state.dealerHand.value, 0, state.stakeSeconds
                )
            )
        } else {
            state.copy(
                playerHand = newPlayer,
                shoeIndex = newIndex,
                canDoubleDown = false
            )
        }
    }

    /**
     * Player stands. Dealer draws.
     */
    fun stand(state: BlackjackState, stakeSeconds: Long): BlackjackState {
        var dealerHand = Hand(state.dealerHand.cards.toMutableList())
        var shoeIdx = state.shoeIndex
        var dealerDone = false

        while (!dealerDone) {
            val value = dealerHand.value
            val isSoft17 = dealerHand.isSoft && value == 17
            when {
                value < 17 -> {
                    dealerHand.add(state.shoe[shoeIdx])
                    shoeIdx++
                }
                value == 17 && isSoft17 -> {
                    // Dealer stands on soft 17 (S17 rule)
                    dealerDone = true
                }
                else -> dealerDone = true
            }
        }

        val playerValue = state.playerHand.value
        val dealerValue = dealerHand.value
        val outcome = when {
            dealerHand.isBusted -> BlackjackOutcome.DEALER_BUST
            playerValue > dealerValue -> BlackjackOutcome.PLAYER_WIN
            playerValue < dealerValue -> BlackjackOutcome.DEALER_WIN
            else -> BlackjackOutcome.PUSH
        }

        val profit = when (outcome) {
            BlackjackOutcome.DEALER_BUST, BlackjackOutcome.PLAYER_WIN -> stakeSeconds
            else -> 0L
        }
        val loss = when (outcome) {
            BlackjackOutcome.DEALER_WIN -> stakeSeconds
            else -> 0L
        }

        return state.copy(
            dealerHand = dealerHand,
            shoeIndex = shoeIdx,
            isPlayerDone = true,
            isDealerDone = true,
            canDoubleDown = false,
            result = BlackjackResult(outcome, playerValue, dealerValue, profit, loss)
        )
    }

    /**
     * Player doubles down.
     */
    fun doubleDown(state: BlackjackState, additionalStake: Long): BlackjackState {
        val newPlayer = Hand(state.playerHand.cards.toMutableList())
        newPlayer.add(state.shoe[state.shoeIndex])

        return if (newPlayer.isBusted) {
            state.copy(
                playerHand = newPlayer,
                shoeIndex = state.shoeIndex + 1,
                isPlayerDone = true,
                isDealerDone = true,
                canDoubleDown = false,
                stakeSeconds = state.stakeSeconds + additionalStake,
                result = BlackjackResult(
                    BlackjackOutcome.PLAYER_BUST,
                    newPlayer.value, state.dealerHand.value, 0,
                    state.stakeSeconds + additionalStake
                )
            )
        } else {
            stand(
                state.copy(
                    playerHand = newPlayer,
                    shoeIndex = state.shoeIndex + 1,
                    canDoubleDown = false,
                    stakeSeconds = state.stakeSeconds + additionalStake
                ),
                stakeSeconds = state.stakeSeconds + additionalStake
            )
        }
    }

    /**
     * Calculate final profit for blackjack win.
     */
    fun calculateBlackjackProfit(stakeSeconds: Long): Long {
        return (stakeSeconds * BLACKJACK_PAYOUT).toLong()
    }
}
