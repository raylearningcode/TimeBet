package com.timebet.app.features.casino.crash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.design.theme.*
import com.timebet.app.features.casino.coinflip.StakeSelector
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CrashPhase { BETTING, FLYING, CRASHED, CASHED_OUT }

@Composable
fun CrashScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var phase by remember { mutableStateOf(CrashPhase.BETTING) }
    var crashPoint by remember { mutableDoubleStateOf(1.0) }
    var currentMultiplier by remember { mutableDoubleStateOf(1.0) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var cashOutMultiplier by remember { mutableDoubleStateOf(0.0) }
    var profitSeconds by remember { mutableLongStateOf(0L) }
    var isFlying by remember { mutableStateOf(false) }

    // History of multiplier points for the trail graph
    var multiplierHistory by remember { mutableStateOf(listOf(Pair(0L, 1.0))) }

    // Screen shake on crash
    val shakeOffsetX = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
    }

    fun startGame() {
        scope.launch {
            shakeOffsetX.snapTo(0f)
            flashAlpha.snapTo(0f)
            val point = ServiceLocator.timeBankRepository.generateCrashPoint()
            crashPoint = point
            currentMultiplier = 1.0
            elapsedMs = 0
            multiplierHistory = listOf(Pair(0L, 1.0))
            phase = CrashPhase.FLYING
            isFlying = true
            var ms = 0L
            val history = mutableListOf(Pair(0L, 1.0))

            while (isFlying) {
                delay(50)
                ms += 50
                elapsedMs = ms
                currentMultiplier = ServiceLocator.timeBankRepository.crashMultiplierAtTime(ms, point)
                history.add(Pair(ms, currentMultiplier))

                // Keep last ~100 points for the trail
                if (history.size > 120) history.removeAt(0)
                multiplierHistory = history.toList()

                // Guard against double-settlement: only crash if still flying.
                // If user cashed out, phase is already CASHED_OUT and we skip.
                if (currentMultiplier >= point && phase == CrashPhase.FLYING) {
                    isFlying = false
                    currentMultiplier = point
                    phase = CrashPhase.CRASHED

                    // Screen shake effect
                    repeat(6) { i ->
                        shakeOffsetX.snapTo(if (i % 2 == 0) 8f else -8f)
                        delay(40)
                    }
                    shakeOffsetX.snapTo(0f)
                    flashAlpha.snapTo(0.6f)
                    flashAlpha.animateTo(0f, animationSpec = tween(400))

                    ServiceLocator.timeBankRepository.settleCasinoRound(
                        gameType = "crash",
                        stakeSeconds = stakeSeconds,
                        isWin = false,
                        profitSeconds = 0,
                        metadataJson = "{\"crash_point\":$point}"
                    )
                    balance = ServiceLocator.timeBankEngine.getBalance()
                }
            }
        }
    }

    fun cashOut() {
        // Guard against double-settlement
        if (phase != CrashPhase.FLYING) return
        isFlying = false
        cashOutMultiplier = currentMultiplier
        val payout = ServiceLocator.timeBankRepository.crashPayout(stakeSeconds, currentMultiplier)
        profitSeconds = payout
        phase = CrashPhase.CASHED_OUT

        scope.launch {
            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "crash",
                stakeSeconds = stakeSeconds,
                isWin = true,
                profitSeconds = payout,
                metadataJson = "{\"crash_point\":$crashPoint,\"cashed_out_at\":$currentMultiplier}"
            )
            balance = ServiceLocator.timeBankEngine.getBalance()
        }
    }

    // Dynamic multiplier color
    val multiplierColor = when {
        currentMultiplier < 2.0 -> TimeBetWhite
        currentMultiplier < 5.0 -> TimeBetGreen
        currentMultiplier < 10.0 -> TimeBetAmber
        else -> TimeBetRed
    }

    // Trail line color gradient
    val trailColor = when {
        currentMultiplier < 2.0 -> TimeBetWhite
        currentMultiplier < 5.0 -> TimeBetGreen
        currentMultiplier < 10.0 -> TimeBetGoldLight
        else -> TimeBetRed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
            .graphicsLayer { translationX = shakeOffsetX.value }
    ) {
        // Red crash flash overlay
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TimeBetRed.copy(alpha = flashAlpha.value))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
            }
            Text("Crash", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        when (phase) {
            CrashPhase.BETTING -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Crash", style = TimeBetTypography.headlineLarge, color = TimeBetWhite)
                    Text("Cash out before it crashes", style = TimeBetTypography.bodyMedium, color = TimeBetTextTertiary)
                    Text("8% house edge · Most crash under 3x", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(24.dp))
                    StakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { startGame() },
                        enabled = stakeSeconds > 0 && stakeSeconds <= balance,
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("START", style = TimeBetTypography.labelLarge) }
                }
            }

            CrashPhase.FLYING -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(40.dp))

                    // Multiplier trail graph
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        if (multiplierHistory.size < 2) return@Canvas

                        val maxMultiplier = maxOf(crashPoint * 1.2, 2.5)
                        val points = multiplierHistory.map { (ms, mult) ->
                            val x = (ms.toFloat() / (multiplierHistory.last().first.coerceAtLeast(1))) * size.width
                            val y = size.height - ((mult / maxMultiplier) * size.height).toFloat()
                            Offset(x.coerceIn(0f, size.width), y.coerceIn(0f, size.height))
                        }

                        // Grid lines
                        for (level in 1..4) {
                            val y = size.height - ((level.toFloat() / maxMultiplier) * size.height).toFloat()
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )
                        }

                        // Trail line
                        if (points.size >= 2) {
                            for (i in 0 until points.size - 1) {
                                val progress = i.toFloat() / points.size
                                val alpha = (0.3f + progress * 0.7f)
                                drawLine(
                                    color = trailColor.copy(alpha = alpha),
                                    start = points[i],
                                    end = points[i + 1],
                                    strokeWidth = 3f
                                )
                            }
                        }

                        // Current position dot
                        val lastPoint = points.last()
                        drawCircle(
                            color = multiplierColor,
                            radius = 8f,
                            center = lastPoint
                        )
                        drawCircle(
                            color = multiplierColor.copy(alpha = 0.3f),
                            radius = 16f,
                            center = lastPoint
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Multiplier display
                    Text(
                        String.format("%.2fx", currentMultiplier),
                        style = TimeBetTypography.displayLarge,
                        color = multiplierColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val totalPayout = ServiceLocator.timeBankRepository.crashPayout(stakeSeconds, currentMultiplier) + stakeSeconds
                    Text(
                        TimeFormatter.formatMinutesSeconds(totalPayout),
                        style = TimeBetTypography.headlineMedium,
                        color = TimeBetWhite
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-cashout presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1.5, 2.0, 3.0, 5.0).forEach { target ->
                            val reached = currentMultiplier >= target
                            OutlinedButton(
                                onClick = {
                                    if (currentMultiplier >= target) cashOut()
                                },
                                enabled = !reached, // show as enabled only before reaching
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (reached) TimeBetGreen.copy(alpha = 0.5f) else TimeBetBorderLight
                                    )
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (reached) TimeBetGreen else TimeBetTextSecondary
                                )
                            ) {
                                Text(
                                    "${target}x",
                                    style = TimeBetTypography.labelSmall,
                                    color = if (reached) TimeBetGreen else TimeBetTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { cashOut() },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetGreen, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("CASH OUT · ${TimeFormatter.formatMinutesSeconds(totalPayout)}", style = TimeBetTypography.labelLarge)
                    }
                }
            }

            CrashPhase.CRASHED -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💥", style = TimeBetTypography.displayLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("CRASHED!", style = TimeBetTypography.displayMedium, color = TimeBetRed, fontWeight = FontWeight.Bold)
                    Text("at ${String.format("%.2fx", crashPoint)}x", style = TimeBetTypography.headlineMedium, color = TimeBetRed)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("-${TimeFormatter.formatMinutesSeconds(stakeSeconds)}", style = TimeBetTypography.headlineMedium, color = TimeBetRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { phase = CrashPhase.BETTING },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("START AGAIN", style = TimeBetTypography.labelLarge) }
                }
            }

            CrashPhase.CASHED_OUT -> {
                val totalPayout = profitSeconds + stakeSeconds
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("CASHED OUT!", style = TimeBetTypography.displayMedium, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                    Text("at ${String.format("%.2fx", cashOutMultiplier)}x", style = TimeBetTypography.headlineMedium, color = TimeBetGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(TimeFormatter.formatMinutesSeconds(totalPayout), style = TimeBetTypography.displayLarge, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                    Text("+${TimeFormatter.formatMinutesSeconds(profitSeconds)} profit", style = TimeBetTypography.bodyMedium, color = TimeBetGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Crashed at ${String.format("%.2fx", crashPoint)}x", style = TimeBetTypography.bodyMedium, color = TimeBetRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { phase = CrashPhase.BETTING },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("START AGAIN", style = TimeBetTypography.labelLarge) }
                }
            }

        }

    }
}
