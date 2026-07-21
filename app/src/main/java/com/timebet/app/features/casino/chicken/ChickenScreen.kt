package com.timebet.app.features.casino.chicken

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.ChickenEngine
import com.timebet.app.design.theme.*
import com.timebet.app.features.casino.coinflip.StakeSelector
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ChickenPhase { BETTING, CROSSING, CASHED_OUT, CRASHED }

@Composable
fun ChickenScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var totalLanes by remember { mutableIntStateOf(6) }
    var phase by remember { mutableStateOf(ChickenPhase.BETTING) }
    var gameState by remember { mutableStateOf<ChickenEngine.ChickenGameState?>(null) }
    var profit by remember { mutableLongStateOf(0L) }
    var crashLane by remember { mutableIntStateOf(-1) }

    // Car animation state per lane
    val carOffsets = remember { (0..10).map { Animatable((it * 0.3f) % 1f) }.toMutableList() }

    // Animate cars
    LaunchedEffect(phase) {
        if (phase == ChickenPhase.CROSSING) {
            carOffsets.forEachIndexed { i, offset ->
                launch {
                    val speed = 2000L + (i * 300L)
                    while (phase == ChickenPhase.CROSSING) {
                        offset.animateTo(1f, animationSpec = tween(speed.toInt(), easing = LinearEasing))
                        offset.snapTo(0f)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { balance = ServiceLocator.timeBankEngine.getBalance() }

    fun startCrossing() {
        gameState = ServiceLocator.timeBankRepository.createChickenGame(totalLanes, stakeSeconds)
        crashLane = -1
        phase = ChickenPhase.CROSSING
    }

    fun crossNext() {
        val state = gameState ?: return
        scope.launch {
            delay(400) // Brief suspense
            val next = ServiceLocator.timeBankRepository.tryCrossLane(state)
            gameState = next
            if (next.isCrashed) {
                crashLane = next.lanesCrossed
                ServiceLocator.timeBankRepository.settleCasinoRound(
                    "chicken", stakeSeconds, false, 0,
                    "{\"lanes\":${next.totalLanes},\"crossed\":${next.lanesCrossed},\"crashed\":true}"
                )
                balance = ServiceLocator.timeBankEngine.getBalance()
                phase = ChickenPhase.CRASHED
            }
        }
    }

    fun cashOut() {
        val state = gameState ?: return
        val cashed = ServiceLocator.timeBankRepository.cashOutChicken(state, stakeSeconds)
        gameState = cashed
        profit = cashed.profitSeconds
        scope.launch {
            ServiceLocator.timeBankRepository.settleCasinoRound(
                "chicken", stakeSeconds, true, profit,
                "{\"lanes\":${cashed.totalLanes},\"crossed\":${cashed.lanesCrossed},\"cashed_out\":true}"
            )
            balance = ServiceLocator.timeBankEngine.getBalance()
            phase = ChickenPhase.CASHED_OUT
        }
    }

    Column(Modifier.fillMaxSize().background(TimeBetBlack)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite) }
            Text("Chicken", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

            // ── Road Canvas ──
            Box(
                Modifier.weight(1f).fillMaxWidth().background(TimeBetSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val state = gameState
                if (state != null && phase != ChickenPhase.BETTING) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        val laneHeight = size.height / state.totalLanes
                        val chickenX = size.width * 0.5f

                        state.totalLanes.let { lanes ->
                            for (i in 0 until lanes) {
                                val laneTop = (lanes - 1 - i) * laneHeight
                                val isCrashedLane = i + 1 == crashLane
                                val isCrossed = i < state.lanesCrossed
                                val isCurrent = i == state.lanesCrossed && state.isActive

                                // Lane background
                                val laneColor = when {
                                    isCrashedLane -> Color(0xFF661111)
                                    isCrossed -> Color(0xFF112211)
                                    else -> Color(0xFF1A1A1A)
                                }
                                drawRect(laneColor, Offset(0f, laneTop.toFloat()), androidx.compose.ui.geometry.Size(size.width, laneHeight))

                                // Lane line
                                drawLine(
                                    Color.White.copy(alpha = 0.1f), Offset(0f, laneTop.toFloat()),
                                    Offset(size.width, laneTop.toFloat()), 2f
                                )

                                // Moving car
                                if (!isCrossed && !isCrashedLane) {
                                    val carX = carOffsets.getOrElse(i) { Animatable(0f) }.value * size.width
                                    val carY = laneTop + laneHeight / 2
                                    drawRect(
                                        Color.Yellow.copy(alpha = 0.7f),
                                        Offset((carX - 20).toFloat(), (carY - 6).toFloat()),
                                        androidx.compose.ui.geometry.Size(40f, 12f)
                                    )
                                }

                                // Chicken position
                                if (isCurrent) {
                                    val chickenY = laneTop + laneHeight / 2
                                    drawCircle(Color.White, 12f, Offset(chickenX, chickenY))
                                    // Chicken body
                                    drawCircle(Color.White.copy(alpha = 0.5f), 16f, Offset(chickenX, chickenY))
                                }
                            }
                        }

                        // Finish line at top
                        drawLine(Color.Green.copy(alpha = 0.4f), Offset(0f, 0f), Offset(size.width, 0f), 4f)
                    }
                } else {
                    Text("🐔", style = TimeBetTypography.displayLarge)
                    Text("Chicken Road", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                }
            }

            Spacer(Modifier.height(12.dp))

            when (phase) {
                ChickenPhase.BETTING -> {
                    Text("Lanes: $totalLanes", style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
                    Slider(value = totalLanes.toFloat(), onValueChange = { totalLanes = it.toInt().coerceIn(4, 10) }, valueRange = 4f..10f, steps = 5,
                        colors = SliderDefaults.colors(thumbColor = TimeBetWhite, activeTrackColor = TimeBetWhite, inactiveTrackColor = TimeBetBorder))
                    Spacer(Modifier.height(8.dp))
                    StakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { startCrossing() },
                        enabled = stakeSeconds in 1..balance,
                        colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("CROSS ROAD", style = TimeBetTypography.labelLarge) }
                }
                ChickenPhase.CROSSING -> {
                    val state = gameState!!
                    val mult = ChickenEngine.calculateMultiplier(state.lanesCrossed, state.totalLanes)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Triple("Lanes", "${state.lanesCrossed}/${state.totalLanes}", TimeBetWhite).let { (l, v, c) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, style = TimeBetTypography.headlineMedium, color = c, fontWeight = FontWeight.Bold)
                                Text(l, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            }
                        }
                        Triple("Multiplier", String.format("%.2fx", mult), TimeBetGreen).let { (l, v, c) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, style = TimeBetTypography.headlineMedium, color = c, fontWeight = FontWeight.Bold)
                                Text(l, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { crossNext() }, colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("CROSS", style = TimeBetTypography.labelLarge) }
                        if (state.lanesCrossed > 0) {
                            Button(onClick = { cashOut() }, colors = ButtonDefaults.buttonColors(TimeBetGreen, TimeBetBlack),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("CASH OUT · ${TimeFormatter.formatMinutesShort(ServiceLocator.timeBankRepository.chickenPayout(stakeSeconds, state))}",
                                    style = TimeBetTypography.labelLarge)
                            }
                        }
                    }
                }
                ChickenPhase.CRASHED -> {
                    Text("💥 SPLAT!", style = TimeBetTypography.displayMedium, color = TimeBetRed, fontWeight = FontWeight.Bold)
                    Text("-${TimeFormatter.formatMinutesSeconds(stakeSeconds)}", style = TimeBetTypography.headlineMedium, color = TimeBetRed)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { phase = ChickenPhase.BETTING; gameState = null; crashLane = -1; profit = 0L },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("CROSS AGAIN", style = TimeBetTypography.labelLarge) }
                }
                ChickenPhase.CASHED_OUT -> {
                    Text("🐔 SAFE!", style = TimeBetTypography.displayMedium, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                    Text("+${TimeFormatter.formatMinutesSeconds(profit)}", style = TimeBetTypography.headlineMedium, color = TimeBetGreen)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { phase = ChickenPhase.BETTING; gameState = null; crashLane = -1; profit = 0L },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("CROSS AGAIN", style = TimeBetTypography.labelLarge) }
                }
            }
        }
    }
}
