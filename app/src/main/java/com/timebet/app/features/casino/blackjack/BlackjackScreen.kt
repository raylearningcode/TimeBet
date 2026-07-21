package com.timebet.app.features.casino.blackjack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.BlackjackEngine
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BJPhase { BETTING, PLAYING, RESULT }

@Composable
fun BlackjackScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var phase by remember { mutableStateOf(BJPhase.BETTING) }
    var gameState by remember { mutableStateOf<BlackjackEngine.BlackjackState?>(null) }
    var finalResult by remember { mutableStateOf<BlackjackEngine.BlackjackResult?>(null) }

    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
    }

    fun settleRound(result: BlackjackEngine.BlackjackResult, totalStake: Long, isBlackjack: Boolean = false) {
        scope.launch {
            val isPush = result.outcome == BlackjackEngine.BlackjackOutcome.PUSH
            if (!isPush) {
                val profit = when (result.outcome) {
                    BlackjackEngine.BlackjackOutcome.PLAYER_BLACKJACK -> ServiceLocator.timeBankRepository.blackjackProfit(totalStake)
                    BlackjackEngine.BlackjackOutcome.DEALER_BUST, BlackjackEngine.BlackjackOutcome.PLAYER_WIN -> totalStake
                    else -> 0L
                }

                ServiceLocator.timeBankRepository.settleCasinoRound(
                    gameType = "blackjack",
                    stakeSeconds = totalStake,
                    isWin = profit > 0,
                    profitSeconds = profit,
                    metadataJson = "{\"player\":${result.playerValue},\"dealer\":${result.dealerValue},\"outcome\":\"${result.outcome.name}\"}"
                )
            }
            balance = ServiceLocator.timeBankEngine.getBalance()
            finalResult = result
            phase = BJPhase.RESULT
        }
    }

    fun deal() {
        val state = ServiceLocator.timeBankRepository.dealBlackjack().copy(stakeSeconds = stakeSeconds)
        gameState = state
        phase = BJPhase.PLAYING

        // Check for instant results
        if (state.result != null) {
            settleRound(state.result!!, state.stakeSeconds, isBlackjack = state.result!!.outcome == BlackjackEngine.BlackjackOutcome.PLAYER_BLACKJACK)
        }
    }

    fun stand() {
        val state = gameState ?: return
        val newState = ServiceLocator.timeBankRepository.blackjackStand(state, state.stakeSeconds)
        gameState = newState
        if (newState.result != null) {
            settleRound(newState.result!!, newState.stakeSeconds)
        }
    }

    fun hit() {
        val state = gameState ?: return
        val newState = ServiceLocator.timeBankRepository.blackjackHit(state)
        gameState = newState
        if (newState.result != null) {
            settleRound(newState.result!!, newState.stakeSeconds)
        } else if (newState.playerHand.value >= 21) {
            // Auto-stand when player reaches 21 — no point hitting further
            stand()
        }
    }

    fun doubleDown() {
        val state = gameState ?: return
        val newState = ServiceLocator.timeBankRepository.blackjackDoubleDown(state, stakeSeconds)
        gameState = newState
        if (newState.result != null) {
            settleRound(newState.result!!, newState.stakeSeconds)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
            }
            Text("Blackjack", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        when (phase) {
            BJPhase.BETTING -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Place Your Bet", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Dealer stands on soft 17 · Blackjack pays 3:2", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(24.dp))

                    com.timebet.app.features.casino.coinflip.StakeSelector(
                        balance = balance,
                        stake = stakeSeconds,
                        onStakeChange = { stakeSeconds = it }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { deal() },
                        enabled = stakeSeconds > 0 && stakeSeconds <= balance,
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("DEAL", style = TimeBetTypography.labelLarge) }
                }
            }

            BJPhase.PLAYING -> {
                val state = gameState!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dealer's hand
                    Text("Dealer", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.dealerHand.cards.forEachIndexed { i, card ->
                            CardView(card = card, faceUp = i == 0 || state.isDealerDone)
                        }
                    }
                    Text(
                        if (state.isDealerDone) "${state.dealerHand.value}" else "${state.dealerHand.cards.first().value}+?",
                        style = TimeBetTypography.labelLarge,
                        color = TimeBetWhite
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = TimeBetBorder)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Player's hand
                    Text("Your Hand", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.playerHand.cards.forEach { card ->
                            CardView(card = card, faceUp = true)
                        }
                    }
                    Text(
                        "${state.playerHand.value}${if (state.playerHand.isSoft) " (soft)" else ""}",
                        style = TimeBetTypography.headlineMedium,
                        color = TimeBetWhite
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Action buttons
                    if (!state.isPlayerDone && state.result == null && state.playerHand.value < 21) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { hit() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(TimeBetBorderLight)
                                )
                            ) { Text("Hit", color = TimeBetWhite) }

                            OutlinedButton(
                                onClick = { stand() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(TimeBetBorderLight)
                                )
                            ) { Text("Stand", color = TimeBetWhite) }

                            if (state.canDoubleDown && balance >= stakeSeconds * 2) {
                                OutlinedButton(
                                    onClick = { doubleDown() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(TimeBetGreen)
                                    )
                                ) { Text("2x", color = TimeBetGreen) }
                            }
                        }
                    }
                }
            }

            BJPhase.RESULT -> {
                // Show result inline above the BETTING UI which will reappear after 2s
                finalResult?.let { result ->
                    val isWin = result.outcome in setOf(
                        BlackjackEngine.BlackjackOutcome.PLAYER_BLACKJACK,
                        BlackjackEngine.BlackjackOutcome.DEALER_BUST,
                        BlackjackEngine.BlackjackOutcome.PLAYER_WIN,
                        BlackjackEngine.BlackjackOutcome.PLAYER_WIN_BLACKJACK
                    )
                    val isPush = result.outcome == BlackjackEngine.BlackjackOutcome.PUSH

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            when {
                                isPush -> "PUSH"
                                isWin -> "YOU WIN!"
                                else -> "DEALER WINS"
                            },
                            style = TimeBetTypography.headlineLarge,
                            color = when {
                                isPush -> TimeBetAmber
                                isWin -> TimeBetGreen
                                else -> TimeBetRed
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Player: ${result.playerValue} · Dealer: ${result.dealerValue}",
                            style = TimeBetTypography.bodyLarge,
                            color = TimeBetTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            when {
                                isWin -> "+${TimeFormatter.formatMinutesSeconds(result.profitSeconds)}"
                                isPush -> "±0"
                                else -> "-${TimeFormatter.formatMinutesSeconds(result.lossSeconds)}"
                            },
                            style = TimeBetTypography.headlineMedium,
                            color = when {
                                isWin -> TimeBetGreen
                                isPush -> TimeBetAmber
                                else -> TimeBetRed
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { phase = BJPhase.BETTING; gameState = null; finalResult = null },
                            colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("DEAL AGAIN", style = TimeBetTypography.labelLarge) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardView(card: BlackjackEngine.Card, faceUp: Boolean) {
    Box(
        modifier = Modifier
            .size(56.dp, 80.dp)
            .background(
                if (faceUp) TimeBetSurfaceElevated else TimeBetBorderLight,
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (faceUp) {
            Text(card.displayRank, style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
        } else {
            Text("?", style = TimeBetTypography.headlineMedium, color = TimeBetTextTertiary)
        }
    }
}
