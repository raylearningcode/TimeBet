package com.timebet.app.features.casino.baccarat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.BaccaratEngine
import com.timebet.app.design.theme.*
import com.timebet.app.features.casino.coinflip.StakeSelector
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BaccaratPhase { BETTING, DEALING, RESULT }

@Composable
fun BaccaratScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var phase by remember { mutableStateOf(BaccaratPhase.BETTING) }
    var betOn by remember { mutableStateOf("player") }
    var result by remember { mutableStateOf<BaccaratEngine.BaccaratResult?>(null) }
    var profit by remember { mutableLongStateOf(0L) }
    var isWin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { balance = ServiceLocator.timeBankEngine.getBalance() }

    fun deal() {
        scope.launch {
            phase = BaccaratPhase.DEALING
            delay(600) // Brief deal animation delay
            val r = ServiceLocator.timeBankRepository.dealBaccarat()
            result = r
            isWin = ServiceLocator.timeBankRepository.baccaratIsWin(r.outcome, betOn)
            profit = ServiceLocator.timeBankRepository.baccaratPayout(stakeSeconds, r.outcome, betOn)

            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "baccarat",
                stakeSeconds = stakeSeconds,
                isWin = isWin,
                profitSeconds = profit,
                metadataJson = "{\"player\":${r.playerHand.total},\"banker\":${r.bankerHand.total},\"bet\":\"$betOn\",\"outcome\":\"${r.outcome.name}\"}"
            )
            balance = ServiceLocator.timeBankEngine.getBalance()
            phase = BaccaratPhase.RESULT
        }
    }

    Column(Modifier.fillMaxSize().background(TimeBetBlack)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite) }
            Text("Baccarat", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

            // Card display
            if (result != null) {
                val r = result!!
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Player hand
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PLAYER", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            r.playerHand.cards.forEach { card ->
                                MiniCard(card.rank, card.suitSymbol)
                            }
                        }
                        Text("${r.playerHand.total}", style = TimeBetTypography.headlineMedium, color = TimeBetWhite, fontWeight = FontWeight.Bold)
                    }
                    // Banker hand
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BANKER", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            r.bankerHand.cards.forEach { card ->
                                MiniCard(card.rank, card.suitSymbol)
                            }
                        }
                        Text("${r.bankerHand.total}", style = TimeBetTypography.headlineMedium, color = TimeBetWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Result
            AnimatedVisibility(visible = phase == BaccaratPhase.RESULT, enter = fadeIn(spring())) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            isWin && betOn == "tie" -> "TIE WIN!"
                            isWin -> "YOU WIN!"
                            result?.outcome == BaccaratEngine.Outcome.TIE && betOn != "tie" -> "PUSH"
                            else -> "YOU LOSE"
                        },
                        style = TimeBetTypography.headlineLarge,
                        color = when {
                            isWin -> TimeBetGreen
                            result?.outcome == BaccaratEngine.Outcome.TIE && betOn != "tie" -> TimeBetAmber
                            else -> TimeBetRed
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            isWin -> "+${TimeFormatter.formatMinutesSeconds(profit)}"
                            result?.outcome == BaccaratEngine.Outcome.TIE && betOn != "tie" -> "±0"
                            else -> "-${TimeFormatter.formatMinutesSeconds(stakeSeconds)}"
                        },
                        style = TimeBetTypography.headlineMedium,
                        color = if (isWin) TimeBetGreen else TimeBetRed
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bet chips
            if (phase != BaccaratPhase.DEALING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("player" to "PLAYER", "tie" to "TIE", "banker" to "BANKER").forEach { (key, label) ->
                        FilterChip(
                            selected = betOn == key,
                            onClick = { if (phase == BaccaratPhase.BETTING) betOn = key },
                            label = { Text(label, style = TimeBetTypography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack, containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                StakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (phase == BaccaratPhase.RESULT) { phase = BaccaratPhase.BETTING; result = null }
                        else deal()
                    },
                    enabled = (phase == BaccaratPhase.BETTING || phase == BaccaratPhase.RESULT) && stakeSeconds in 1..balance,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (phase == BaccaratPhase.RESULT) "DEAL AGAIN" else "DEAL", style = TimeBetTypography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MiniCard(rank: Int, suit: String) {
    val color = if (suit in listOf("♥", "♦")) TimeBetRed else TimeBetWhite
    Box(
        Modifier.size(44.dp, 62.dp).background(TimeBetSurfaceElevated, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (rank) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$rank" },
                style = TimeBetTypography.labelLarge, color = color
            )
            Text(suit, style = TimeBetTypography.labelSmall, color = color)
        }
    }
}
