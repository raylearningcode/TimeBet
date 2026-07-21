package com.timebet.app.features.casino.coinflip

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.CoinFlipResult
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CoinFlipPhase { SETUP, ANIMATING, RESULT }

@Composable
fun CoinFlipScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(CoinFlipPhase.SETUP) }
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var betOnHeads by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<CoinFlipResult?>(null) }
    var showingHeads by remember { mutableStateOf(true) }

    // Animation states
    val coinRotation = remember { Animatable(0f) }
    val coinScale = remember { Animatable(1f) }
    var flashColor by remember { mutableStateOf(TimeBetSurfaceElevated) }

    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
    }

    fun executeFlip() {
        scope.launch {
            phase = CoinFlipPhase.ANIMATING

            val flipResult = ServiceLocator.timeBankRepository.flipCoin(stakeSeconds, betOnHeads)
            result = flipResult

            // Smooth coin flip: 3 fast rotations + 2 slow deceleration + spring settle
            coinRotation.snapTo(0f)
            coinScale.snapTo(1f)

            // Phase 1: Fast flips (3 rotations, ~120ms each)
            for (i in 1..3) {
                coinRotation.animateTo(i * 180f, animationSpec = tween(120, easing = LinearEasing))
                coinScale.animateTo(0.7f, animationSpec = tween(60))
                showingHeads = !showingHeads
                coinScale.animateTo(1f, animationSpec = tween(60))
            }

            // Phase 2: Slow deceleration (2 rotations, ~200ms each)
            for (i in 4..5) {
                coinRotation.animateTo(i * 180f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                coinScale.animateTo(0.8f, animationSpec = tween(100))
                showingHeads = !showingHeads
                coinScale.animateTo(1f, animationSpec = tween(100))
            }

            // Settle on result with spring — no snap
            showingHeads = flipResult.coinIsHeads
            val targetRotation = 900f
            coinRotation.animateTo(targetRotation, animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
            coinScale.animateTo(1.05f, animationSpec = spring(dampingRatio = 0.3f, stiffness = 500f))
            coinScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f))

            flashColor = if (flipResult.isWin) TimeBetGreen else TimeBetRed

            // Settle with Time Bank
            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "coin_flip",
                stakeSeconds = stakeSeconds,
                isWin = flipResult.isWin,
                profitSeconds = flipResult.profitSeconds,
                metadataJson = "{\"bet\":\"${if (betOnHeads) "heads" else "tails"}\",\"result\":\"${if (flipResult.coinIsHeads) "heads" else "tails"}\"}"
            )

            balance = ServiceLocator.timeBankEngine.getBalance()
            phase = CoinFlipPhase.RESULT
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TimeBetBlack)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
            }
            Text("Coin Flip", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Coin visual
            val coinBg = when (phase) {
                CoinFlipPhase.RESULT -> if (result?.isWin == true) TimeBetGreen.copy(alpha = 0.2f) else TimeBetRed.copy(alpha = 0.2f)
                CoinFlipPhase.ANIMATING -> flashColor.copy(alpha = 0.15f)
                else -> TimeBetSurfaceElevated
            }

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(coinBg)
                    .graphicsLayer {
                        rotationY = coinRotation.value
                        scaleX = coinScale.value
                        scaleY = coinScale.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    TimeBetGoldLight.copy(alpha = 0.3f),
                                    TimeBetGoldLight.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(3.dp, TimeBetGoldLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (phase) {
                            CoinFlipPhase.SETUP -> if (betOnHeads) "H" else "T"
                            else -> if (showingHeads) "H" else "T"
                        },
                        style = TimeBetTypography.displayLarge,
                        color = when {
                            phase == CoinFlipPhase.RESULT && result?.isWin == true -> TimeBetGreen
                            phase == CoinFlipPhase.RESULT && result?.isWin == false -> TimeBetRed
                            else -> TimeBetGoldLight
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bet selector (Heads/Tails) — always visible
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = betOnHeads,
                    onClick = { if (phase == CoinFlipPhase.SETUP) betOnHeads = true },
                    label = { Text("Heads", color = if (betOnHeads) TimeBetBlack else TimeBetWhite) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, containerColor = TimeBetSurfaceElevated),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilterChip(
                    selected = !betOnHeads,
                    onClick = { if (phase == CoinFlipPhase.SETUP) betOnHeads = false },
                    label = { Text("Tails", color = if (!betOnHeads) TimeBetBlack else TimeBetWhite) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, containerColor = TimeBetSurfaceElevated),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            StakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
            Spacer(modifier = Modifier.height(24.dp))

            // FLIP button — disabled during animation/result, re-enabled when SETUP
            val canFlip = (phase == CoinFlipPhase.SETUP || phase == CoinFlipPhase.RESULT) && stakeSeconds > 0 && stakeSeconds <= balance
            Button(
                onClick = {
                    if (phase == CoinFlipPhase.RESULT) { phase = CoinFlipPhase.SETUP; result = null; flashColor = TimeBetSurfaceElevated }
                    else executeFlip()
                },
                enabled = canFlip,
                colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    when { phase == CoinFlipPhase.ANIMATING -> "FLIPPING…"; phase == CoinFlipPhase.RESULT -> "FLIP AGAIN"; else -> "FLIP" },
                    style = TimeBetTypography.labelLarge
                )
            }

            // Result overlay — shown on top of controls during RESULT phase
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedVisibility(
                visible = phase == CoinFlipPhase.RESULT,
                enter = fadeIn(spring()) + slideInVertically(spring()) { it / 2 },
                exit = fadeOut()
            ) {
                result?.let { res ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (res.isWin) "YOU WIN!" else "YOU LOSE",
                            style = TimeBetTypography.headlineMedium,
                            color = if (res.isWin) TimeBetGreen else TimeBetRed,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (res.isWin) "+${TimeFormatter.formatMinutesSeconds(res.profitSeconds)}"
                            else "-${TimeFormatter.formatMinutesSeconds(res.lossSeconds)}",
                            style = TimeBetTypography.headlineMedium,
                            color = if (res.isWin) TimeBetGreen else TimeBetRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun StakeSelector(
    balance: Long,
    stake: Long,
    onStakeChange: (Long) -> Unit,
    compact: Boolean = false
) {
    val maxStake = (balance * com.timebet.app.util.TimeBetConstants.MAX_STAKE_PERCENTAGE).toLong().coerceAtLeast(60L)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Stake", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            Text(TimeFormatter.formatMinutesSeconds(stake), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.timebet.app.util.TimeBetConstants.QUICK_STAKES_SECONDS.forEach { quickStake ->
                val enabled = quickStake <= maxStake
                FilterChip(
                    selected = stake == quickStake,
                    onClick = { if (enabled) onStakeChange(quickStake) },
                    label = { Text(TimeFormatter.formatMinutesShort(quickStake), style = TimeBetTypography.labelSmall, color = when { stake == quickStake -> TimeBetBlack; enabled -> TimeBetWhite; else -> TimeBetTextTertiary }) },
                    enabled = enabled,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, containerColor = TimeBetSurfaceElevated),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStakeChange((stake - 60).coerceAtLeast(60)) }, enabled = stake > 60) {
                Icon(Icons.Filled.Remove, "Less", tint = if (stake > 60) TimeBetWhite else TimeBetTextTertiary)
            }
            Text(TimeFormatter.formatMinutesSeconds(stake), style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            IconButton(onClick = { onStakeChange((stake + 60).coerceAtMost(maxStake)) }, enabled = stake < maxStake) {
                Icon(Icons.Filled.Add, "More", tint = if (stake < maxStake) TimeBetWhite else TimeBetTextTertiary)
            }
        }
    }
}
