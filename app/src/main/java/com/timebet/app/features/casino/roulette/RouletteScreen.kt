package com.timebet.app.features.casino.roulette

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.*
import com.timebet.app.design.theme.*
import com.timebet.app.features.casino.coinflip.StakeSelector
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun RouletteScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var selectedBetType by remember { mutableStateOf<BetType?>(null) }
    var selectedNumber by remember { mutableIntStateOf(-1) }
    var selectedDozen by remember { mutableIntStateOf(-1) }
    var selectedColumn by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf("betting") }
    var spinResult by remember { mutableStateOf<RouletteSpinResult?>(null) }
    var betResult by remember { mutableStateOf<RouletteBetResult?>(null) }

    // Wheel rotation — proper spinning wheel
    val wheelAngle = remember { Animatable(0f) }
    var displayNumber by remember { mutableIntStateOf(0) }
    var pointerColor by remember { mutableStateOf(TimeBetGoldLight) }

    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
    }

    fun buildBet(): RouletteBet? {
        val type = selectedBetType ?: return null
        return RouletteBet(
            type = type,
            stakeSeconds = stakeSeconds,
            numbers = if (selectedNumber >= 0) listOf(selectedNumber) else emptyList(),
            dozens = if (selectedDozen > 0) listOf(selectedDozen) else emptyList(),
            columns = if (selectedColumn > 0) listOf(selectedColumn) else emptyList()
        )
    }

    /**
     * Maps a roulette number (0-36) to an angle on the wheel (in degrees).
     * European roulette layout — numbers are distributed around the wheel in a specific order.
     */
    fun numberToAngle(number: Int): Float {
        // Standard European roulette wheel order
        val wheelOrder = listOf(
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
            10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
        )
        val index = wheelOrder.indexOf(number)
        return (index * (360f / 37f))
    }

    fun spin() {
        val bet = buildBet() ?: return
        scope.launch {
            phase = "spinning"

            val result = ServiceLocator.timeBankRepository.spinRoulette()
            spinResult = result

            // Smooth multi-phase wheel spin
            val numberAngle = numberToAngle(result.number)
            val spinCount = (5..8).random()
            val targetAngle = spinCount * 360f + (360f - numberAngle)

            wheelAngle.snapTo(0f)

            // Phase 1: Fast spin — 5-8 full rotations in 1200ms
            wheelAngle.animateTo(
                targetAngle * 0.75f,
                animationSpec = tween(1200, easing = LinearEasing)
            )

            // Phase 2: Gradual deceleration — 600ms
            wheelAngle.animateTo(
                targetAngle * 0.95f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )

            // Show result number BEFORE spring — syncs display with wheel settle
            displayNumber = result.number
            pointerColor = when (result.color) {
                RouletteColor.RED -> TimeBetRed
                RouletteColor.GREEN -> TimeBetGreen
                RouletteColor.BLACK -> TimeBetWhite
            }

            // Phase 3: Fine settle with spring physics
            wheelAngle.animateTo(
                targetAngle,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 70f)
            )

            pointerColor = TimeBetGoldLight

            val evaluated = ServiceLocator.timeBankRepository.evaluateRouletteBet(bet, result)
            betResult = evaluated

            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "roulette",
                stakeSeconds = stakeSeconds,
                isWin = evaluated.isWin,
                profitSeconds = evaluated.profitSeconds,
                metadataJson = "{\"number\":${result.number},\"bet_type\":\"${bet.type.name}\",\"payout\":${evaluated.payoutMultiplier}}"
            )

            balance = ServiceLocator.timeBankEngine.getBalance()
            phase = "result"
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
            Text("Roulette", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Roulette Wheel ──
            RouletteWheel(
                angle = wheelAngle.value,
                resultNumber = if (phase == "result") spinResult?.number else null,
                pointerColor = pointerColor,
                displayNumber = displayNumber,
                isSpinning = phase == "spinning",
                phase = phase
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Result text
            AnimatedVisibility(
                visible = phase == "result" && betResult != null,
                enter = fadeIn(spring()) + slideInVertically(spring()) { it / 2 }
            ) {
                betResult?.let { br ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (br.isWin) "YOU WIN!" else "YOU LOSE",
                            style = TimeBetTypography.headlineMedium,
                            color = if (br.isWin) TimeBetGreen else TimeBetRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (br.isWin) "+${TimeFormatter.formatMinutesSeconds(br.profitSeconds)}"
                            else "-${TimeFormatter.formatMinutesSeconds(br.lossSeconds)}",
                            style = TimeBetTypography.labelLarge,
                            color = if (br.isWin) TimeBetGreen else TimeBetRed
                        )
                    }
                }
            }

            if (phase == "betting") {
                val betTypes = listOf(
                    BetType.RED to "Red", BetType.BLACK to "Black",
                    BetType.ODD to "Odd", BetType.EVEN to "Even",
                    BetType.LOW to "1-18", BetType.HIGH to "19-36",
                    BetType.DOZEN to "Dozens", BetType.COLUMN to "Columns",
                    BetType.STRAIGHT to "Number"
                )

                // Bet type chips
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        betTypes.take(6).forEach { (type, label) ->
                            FilterChip(
                                selected = selectedBetType == type,
                                onClick = { selectedBetType = type; selectedNumber = -1; selectedDozen = -1; selectedColumn = -1 },
                                label = { Text(label, style = TimeBetTypography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack, containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedBetType == BetType.STRAIGHT) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.height(180.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items((0..36).toList()) { num ->
                                val bgColor = when {
                                    num == 0 -> TimeBetGreen.copy(alpha = 0.5f)
                                    num in ServiceLocator.rouletteEngine.redNumbers -> TimeBetRed.copy(alpha = 0.3f)
                                    else -> TimeBetSurfaceElevated
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedNumber == num) TimeBetWhite else bgColor)
                                        .clickable { selectedNumber = num }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$num", style = TimeBetTypography.bodyMedium,
                                        color = if (selectedNumber == num) TimeBetBlack else TimeBetWhite)
                                }
                            }
                        }
                    }

                    if (selectedBetType == BetType.DOZEN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1st 12" to 1, "2nd 12" to 2, "3rd 12" to 3).forEach { (label, d) ->
                                FilterChip(selected = selectedDozen == d, onClick = { selectedDozen = d }, label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack, containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                                    shape = RoundedCornerShape(6.dp))
                            }
                        }
                    }

                    if (selectedBetType == BetType.COLUMN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Col 1" to 1, "Col 2" to 2, "Col 3" to 3).forEach { (label, c) ->
                                FilterChip(selected = selectedColumn == c, onClick = { selectedColumn = c }, label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack, containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                                    shape = RoundedCornerShape(6.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                StakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { spin() },
                    enabled = buildBet() != null && stakeSeconds > 0 && stakeSeconds <= balance,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("SPIN", style = TimeBetTypography.labelLarge) }
            }

            if (phase == "result") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { phase = "betting"; spinResult = null; betResult = null; displayNumber = 0 },
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("SPIN AGAIN", style = TimeBetTypography.labelLarge) }
            }
        }
    }
}

// ─── Roulette Wheel Canvas ───

@Composable
private fun RouletteWheel(
    angle: Float,
    resultNumber: Int?,
    pointerColor: Color,
    displayNumber: Int,
    isSpinning: Boolean,
    phase: String
) {
    val wheelBg = when {
        phase == "result" && resultNumber != null -> {
            val clr = when {
                resultNumber == 0 -> TimeBetGreen
                ServiceLocator.rouletteEngine.redNumbers.contains(resultNumber) -> TimeBetRed
                else -> Color(0xFF1A1A2E)
            }
            clr.copy(alpha = 0.3f)
        }
        else -> TimeBetSurfaceElevated
    }

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(wheelBg)
            .border(3.dp, TimeBetGoldLight, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Spinning wheel ring with colored segments
        Canvas(
            modifier = Modifier
                .size(210.dp)
                .rotate(angle)
        ) {
            val canvasSize = size.minDimension
            val strokeWidth = canvasSize * 0.12f
            val outerRadius = canvasSize / 2f - strokeWidth / 2f
            val topLeft = Offset(
                (size.width - outerRadius * 2) / 2f,
                (size.height - outerRadius * 2) / 2f
            )
            val arcSize = Size(outerRadius * 2, outerRadius * 2)

            // European roulette wheel — 37 numbers with alternating colors
            // Standard wheel order
            val wheelOrder = listOf(
                0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
                10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
            )
            val redNumbers = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
            val sweepAngle = 360f / 37f

            wheelOrder.forEachIndexed { index, num ->
                val startAngle = index * sweepAngle - 90f // Start from top
                val color = when {
                    num == 0 -> Color(0xFF005C2E) // Green
                    num in redNumbers -> Color(0xFFC41E3A) // Red
                    else -> Color(0xFF1A1A2E) // Black
                }
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * 0.92f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            // Inner ring
            val innerRadius = outerRadius - strokeWidth - 4f
            drawCircle(
                color = Color(0xFF0A0A0A),
                radius = innerRadius,
                center = Offset(size.width / 2f, size.height / 2f)
            )
        }

        // Pointer at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-2).dp)
                .size(14.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(pointerColor)
        )

        // Center number display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (phase == "betting") "0" else "$displayNumber",
                style = TimeBetTypography.displayMedium,
                color = TimeBetWhite,
                fontWeight = FontWeight.Bold
            )
            if (phase == "result" && resultNumber != null) {
                val colorName = when {
                    resultNumber == 0 -> "GREEN"
                    ServiceLocator.rouletteEngine.redNumbers.contains(resultNumber) -> "RED"
                    else -> "BLACK"
                }
                val textColor = when {
                    resultNumber == 0 -> TimeBetGreen
                    ServiceLocator.rouletteEngine.redNumbers.contains(resultNumber) -> TimeBetRed
                    else -> TimeBetWhite
                }
                Text(colorName, style = TimeBetTypography.labelSmall, color = textColor)
            }
        }
    }
}

/**
 * Maps wheel order index to sector angle for display purposes.
 */
private fun numberIndexOnWheel(number: Int): Int {
    val wheelOrder = listOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
        10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )
    return wheelOrder.indexOf(number)
}
