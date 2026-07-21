package com.timebet.app.features.casino.mines

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.MinesEngine
import com.timebet.app.core.time.MinesGameState
import com.timebet.app.core.time.RiskLevel
import com.timebet.app.design.theme.*
import com.timebet.app.features.casino.coinflip.StakeSelector
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MinesPhase { SETUP, PLAYING, CASHED_OUT, MINED }

@Composable
fun MinesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(MinesPhase.SETUP) }
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var mineCount by remember { mutableIntStateOf(3) }
    var gameState by remember { mutableStateOf<MinesGameState?>(null) }
    var revealedTiles by remember { mutableStateOf(setOf<Int>()) }
    var lastRevealed by remember { mutableIntStateOf(-1) }
    var cashOutAmount by remember { mutableLongStateOf(0L) }

    // Bomb reveal animation
    var showAllBombs by remember { mutableStateOf(false) }
    val bombScales = remember { mutableStateMapOf<Int, Float>() }

    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
    }

    fun startGame() {
        val positions = ServiceLocator.timeBankRepository.generateMinePositions(mineCount)
        gameState = MinesGameState(
            mineCount = mineCount,
            minePositions = positions,
            stakeSeconds = stakeSeconds
        )
        revealedTiles = emptySet()
        lastRevealed = -1
        showAllBombs = false
        bombScales.clear()
        phase = MinesPhase.PLAYING
    }

    fun revealTile(index: Int) {
        val state = gameState ?: return
        if (index in revealedTiles) return
        if (state.isGameOver) return

        val newRevealed = revealedTiles + index
        revealedTiles = newRevealed
        lastRevealed = index

        if (state.minePositions.contains(index)) {
            // Hit a mine — reveal all bombs with animation
            phase = MinesPhase.MINED
            scope.launch {
                // Animate bombs appearing one by one
                val mines = state.minePositions.toList()
                for (i in mines.indices) {
                    val mineIdx = mines[i]
                    if (mineIdx != index) {
                        bombScales[mineIdx] = 0f
                    }
                }
                // Stagger bomb reveals
                for (i in mines.indices) {
                    val mineIdx = mines[i]
                    bombScales[mineIdx] = 1.2f
                    delay(80)
                }
                // Settle bomb scales
                for (mineIdx in mines) {
                    bombScales[mineIdx] = 1f
                }

                ServiceLocator.timeBankRepository.settleCasinoRound(
                    gameType = "mines",
                    stakeSeconds = stakeSeconds,
                    isWin = false,
                    profitSeconds = 0,
                    metadataJson = "{\"mine_count\":$mineCount,\"revealed\":${newRevealed.size - 1},\"hit_mine\":true}"
                )
                balance = ServiceLocator.timeBankEngine.getBalance()
                showAllBombs = true
            }
        }
    }

    fun cashOut() {
        val state = gameState ?: return
        val payout = ServiceLocator.timeBankRepository.calculateMinesPayout(stakeSeconds, mineCount, revealedTiles.size)
        val profit = payout - stakeSeconds
        cashOutAmount = payout
        phase = MinesPhase.CASHED_OUT

        scope.launch {
            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "mines",
                stakeSeconds = stakeSeconds,
                isWin = true,
                profitSeconds = profit.coerceAtLeast(0),
                metadataJson = "{\"mine_count\":$mineCount,\"revealed\":${revealedTiles.size},\"cashed_out\":true}"
            )
            balance = ServiceLocator.timeBankEngine.getBalance()
            bombScales.clear()
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
            Text("Mines", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Mines: 5×5 Grid always visible ──
            MinesGrid(
                gameState = gameState,
                revealedTiles = revealedTiles,
                lastRevealed = lastRevealed,
                phase = phase,
                showAllBombs = showAllBombs,
                bombScales = bombScales,
                onTileClick = { revealTile(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (phase) {
                MinesPhase.SETUP -> {
                    // Game stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GameStat("Mines", "$mineCount")
                        GameStat("Safe Tiles", "${MinesEngine.TOTAL_TILES - mineCount}")
                        GameStat("Risk", ServiceLocator.minesEngine.getRiskLevel(mineCount).label,
                            color = when (ServiceLocator.minesEngine.getRiskLevel(mineCount)) {
                                RiskLevel.LOW -> TimeBetGreen
                                RiskLevel.MEDIUM -> TimeBetAmber
                                RiskLevel.HIGH -> TimeBetRed
                                RiskLevel.EXTREME -> TimeBetRed
                            })
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mine count slider
                    Text("Mine Count", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Slider(
                        value = mineCount.toFloat(),
                        onValueChange = { mineCount = it.toInt().coerceIn(1, 24) },
                        valueRange = 1f..24f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = TimeBetWhite,
                            activeTrackColor = TimeBetWhite,
                            inactiveTrackColor = TimeBetBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { startGame() },
                        enabled = stakeSeconds > 0 && stakeSeconds <= balance,
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("START GAME", style = TimeBetTypography.labelLarge)
                    }
                }

                MinesPhase.PLAYING -> {
                    val state = gameState!!
                    val multiplier = ServiceLocator.timeBankRepository.calculateMinesMultiplier(mineCount, revealedTiles.size)
                    val currentPayout = ServiceLocator.timeBankRepository.calculateMinesPayout(stakeSeconds, mineCount, revealedTiles.size)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GameStat("Multiplier", "${String.format("%.2f", multiplier)}x")
                        GameStat("Cash Out", TimeFormatter.formatMinutesSeconds(currentPayout))
                        GameStat("Safe", "${MinesEngine.TOTAL_TILES - mineCount - revealedTiles.size}")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (revealedTiles.isNotEmpty()) {
                        Button(
                            onClick = { cashOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = TimeBetGreen, contentColor = TimeBetBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("CASH OUT — ${TimeFormatter.formatMinutesSeconds(currentPayout)}", style = TimeBetTypography.labelLarge)
                        }
                    } else {
                        Text("Click a tile to start revealing",
                            style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }

                MinesPhase.CASHED_OUT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GameStat("Payout", TimeFormatter.formatMinutesSeconds(cashOutAmount), TimeBetGreen)
                        GameStat("Profit", "+${TimeFormatter.formatMinutesSeconds((cashOutAmount - stakeSeconds).coerceAtLeast(0))}", TimeBetGreen)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { phase = MinesPhase.SETUP; gameState = null; revealedTiles = emptySet(); showAllBombs = false; bombScales.clear() },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("START", style = TimeBetTypography.labelLarge) }
                }

                MinesPhase.MINED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GameStat("Mines Hit", "${revealedTiles.size}", TimeBetRed)
                        GameStat("Mines", "${gameState?.mineCount ?: mineCount}")
                        GameStat("Lost", TimeFormatter.formatMinutesSeconds(stakeSeconds), TimeBetRed)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { phase = MinesPhase.SETUP; gameState = null; revealedTiles = emptySet(); showAllBombs = false; bombScales.clear() },
                        colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("START", style = TimeBetTypography.labelLarge) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── Mines Grid ───

@Composable
private fun MinesGrid(
    gameState: MinesGameState?,
    revealedTiles: Set<Int>,
    lastRevealed: Int,
    phase: MinesPhase,
    showAllBombs: Boolean,
    bombScales: Map<Int, Float>,
    onTileClick: (Int) -> Unit
) {
    val isInteractive = phase == MinesPhase.PLAYING && gameState?.isGameOver == false
    val isMined = phase == MinesPhase.MINED

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in 0 until MinesEngine.GRID_SIZE) {
            Row {
                for (col in 0 until MinesEngine.GRID_SIZE) {
                    val index = row * MinesEngine.GRID_SIZE + col
                    val isRevealed = index in revealedTiles
                    val isMine = gameState?.minePositions?.contains(index) == true
                    val isLastRevealed = index == lastRevealed
                    val isHitMine = isLastRevealed && isMine && isMined
                    val showBomb = (isMined && isMine && showAllBombs) || isRevealed && isMine

                    val bombScale = bombScales[index] ?: 1f
                    val animatedScale = if (showBomb && bombScales.containsKey(index)) {
                        bombScale
                    } else if (showBomb) 1f else 1f

                    val bgColor = when {
                        isHitMine -> TimeBetRed
                        isRevealed && isMine -> TimeBetRed.copy(alpha = 0.5f)
                        isMined && isMine && showAllBombs -> TimeBetRed.copy(alpha = 0.4f)
                        isRevealed && !isMine -> TimeBetGreen.copy(alpha = 0.15f)
                        else -> TimeBetSurfaceElevated
                    }

                    val borderColor = when {
                        isHitMine -> TimeBetRed
                        isLastRevealed && !isMine -> TimeBetGreen.copy(alpha = 0.5f)
                        isMined && isMine && showAllBombs -> TimeBetRed.copy(alpha = 0.6f)
                        else -> TimeBetBorder
                    }

                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .scale(if (showBomb) animatedScale else 1f)
                            .clickable(enabled = isInteractive && !isRevealed) {
                                onTileClick(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            // Hit mine — show bomb with shake
                            isHitMine -> Text("💣", style = TimeBetTypography.headlineMedium)
                            // Revealed bomb (after game over, all mines shown)
                            isMined && isMine && showAllBombs -> Text("💣", style = TimeBetTypography.headlineMedium)
                            // Revealed safe tile
                            isRevealed && !isMine -> {
                                // Diamond/gem icon for safe tile
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(TimeBetGreen)
                                )
                            }
                            // Unrevealed tile — Stake-style diamond/gem look
                            !isRevealed -> {
                                // Diamond gem icon (rotated square) — like Stake.com mines
                                Box(
                                    modifier = Modifier
                                        .size(if (isInteractive) 14.dp else 10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .rotate(45f)
                                        .background(
                                            if (isInteractive) TimeBetGoldLight.copy(alpha = 0.4f)
                                            else TimeBetTextSecondary.copy(alpha = 0.25f)
                                        )
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = TimeBetWhite) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TimeBetTypography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
    }
}

