package com.timebet.app.features.casino

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.time.*
import com.timebet.app.data.repositories.CasinoDayStats
import com.timebet.app.design.theme.*
import com.timebet.app.navigation.NavRoute
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Casino Landing Screen with inline play — Stake-like UX.
 * Select a game at the top, configure below, stake at bottom, then start.
 */
@Composable
fun CasinoLandingScreen(onGameClick: (String) -> Unit) {
    var bankState by remember { mutableStateOf<DailyTimeBankState?>(null) }
    var casinoStats by remember { mutableStateOf<CasinoDayStats?>(null) }
    var selectedGame by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ServiceLocator.timeBankRepository.observeBalance().collectLatest { state ->
            bankState = state
        }
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        casinoStats = ServiceLocator.timeBankRepository.getDailyCasinoStats(startOfDay, endOfDay)
    }

    val balance = bankState?.currentBalanceSeconds ?: 0
    val isLocked = bankState?.isBonusCapReached == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        // ── Fixed Top: Balance + Bonus ──
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                "CASINO",
                style = TimeBetTypography.labelMedium,
                color = TimeBetTextTertiary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                TimeFormatter.formatMinutesSeconds(balance),
                style = TimeBetTypography.headlineLarge,
                color = TimeBetWhite
            )
            Text("available to play", style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)

            // Daily bonus bar
            Spacer(modifier = Modifier.height(12.dp))
            val totalWins = bankState?.totalWinSeconds ?: 0
            val maxBonus = bankState?.maxDailyBonus ?: 1
            val progressFraction = (totalWins.toFloat() / maxBonus).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Daily Bonus", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                Text(
                    "${TimeFormatter.formatMinutesShort(totalWins)} / ${TimeFormatter.formatMinutesShort(maxBonus)}",
                    style = TimeBetTypography.labelSmall,
                    color = if (isLocked) TimeBetAmber else TimeBetTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(TimeBetBorder, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(progressFraction).height(3.dp)
                        .background(if (isLocked) TimeBetAmber else TimeBetGreen, RoundedCornerShape(2.dp))
                )
            }
            if (isLocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Daily win cap reached — casino disabled", style = TimeBetTypography.labelSmall, color = TimeBetAmber)
            }
        }

        HorizontalDivider(color = TimeBetBorder.copy(alpha = 0.5f))

        // ── Game Selector Chips ──
        val games = listOf(
            InlineGameDef("Coin Flip", "coin_flip", "Heads or tails"),
            InlineGameDef("Mines", "mines", "5×5 grid, push your luck"),
            InlineGameDef("Roulette", "roulette", "European single-zero"),
            InlineGameDef("Blackjack", "blackjack", "Classic Vegas rules"),
            InlineGameDef("Baccarat", "baccarat", "Player vs Banker"),
            InlineGameDef("Crash", "crash", "Cash out before it crashes"),
            InlineGameDef("Chicken", "chicken", "Cross the road"),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            games.forEach { game ->
                FilterChip(
                    selected = selectedGame == game.id,
                    onClick = {
                        if (!isLocked) selectedGame = if (selectedGame == game.id) null else game.id
                    },
                    label = { Text(game.title, style = TimeBetTypography.labelSmall) },
                    enabled = !isLocked,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TimeBetWhite,
                        selectedLabelColor = TimeBetBlack,
                        containerColor = TimeBetSurfaceElevated,
                        labelColor = TimeBetWhite
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        HorizontalDivider(color = TimeBetBorder.copy(alpha = 0.5f))

        // ── Game Area (scrollable + inline play) ──
        Box(modifier = Modifier.weight(1f)) {
            when (selectedGame) {
                "coin_flip" -> InlineCoinFlip(balance = balance, isLocked = isLocked)
                "mines" -> InlineMines(balance = balance, isLocked = isLocked)
                "roulette" -> InlineRoulette(balance = balance, isLocked = isLocked)
                "blackjack" -> InlineBlackjack(balance = balance, isLocked = isLocked)
                "baccarat" -> InlineBaccarat(balance = balance, isLocked = isLocked)
                "crash" -> InlineCrash(balance = balance, isLocked = isLocked)
                "chicken" -> InlineChicken(balance = balance, isLocked = isLocked)
                else -> {
                    // No game selected — show summary and game info cards
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Select a game above to play", style = TimeBetTypography.bodyLarge, color = TimeBetTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))

                        games.forEach { game ->
                            GameInfoCard(
                                title = game.title,
                                subtitle = game.subtitle,
                                onClick = { if (!isLocked) selectedGame = game.id }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Today's Summary", style = TimeBetTypography.labelLarge, color = TimeBetWhite)

                        casinoStats?.let { stats ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                CasinoStatChip("Wagered", TimeFormatter.formatMinutesShort(stats.totalWagered), Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                CasinoStatChip("Won", "+${TimeFormatter.formatMinutesShort(stats.totalProfit)}", Modifier.weight(1f), isPositive = true)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                CasinoStatChip("Lost", "-${TimeFormatter.formatMinutesShort(stats.totalLoss)}", Modifier.weight(1f), isPositive = false)
                                Spacer(modifier = Modifier.width(8.dp))
                                CasinoStatChip("Net", TimeFormatter.formatMinutesShort(stats.netResult), Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            CasinoStatChip("Win Rate", if (stats.totalCount > 0) "${(stats.winRate * 100).toInt()}% (${stats.winCount}/${stats.totalCount})" else "No rounds yet", Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// ─── Game Definitions ───

private data class InlineGameDef(
    val title: String,
    val id: String,
    val subtitle: String
)

// ─── Game Info Card ───

@Composable
private fun GameInfoCard(title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Text(subtitle, style = TimeBetTypography.bodyMedium, color = TimeBetTextTertiary)
            }
        }
    }
}

// ─── Inline Coin Flip ───

@Composable
private fun InlineCoinFlip(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var betOnHeads by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf("betting") }
    var flipResult by remember { mutableStateOf<CoinFlipResult?>(null) }
    var showingHeads by remember { mutableStateOf(true) }
    val coinRotation = remember { Animatable(0f) }
    val coinScale = remember { Animatable(1f) }
    var flashColor by remember { mutableStateOf(TimeBetSurfaceElevated) }

    fun executeFlip() {
        scope.launch {
            phase = "animating"

            val result = ServiceLocator.timeBankRepository.flipCoin(stakeSeconds, betOnHeads)
            flipResult = result

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
            showingHeads = result.coinIsHeads
            val targetRotation = 900f
            coinRotation.animateTo(targetRotation, animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
            coinScale.animateTo(1.05f, animationSpec = spring(dampingRatio = 0.3f, stiffness = 500f))
            coinScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f))

            flashColor = if (result.isWin) TimeBetGreen else TimeBetRed

            // Settle with Time Bank
            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "coin_flip",
                stakeSeconds = stakeSeconds,
                isWin = result.isWin,
                profitSeconds = result.profitSeconds,
                metadataJson = "{\"bet\":\"${if (betOnHeads) "heads" else "tails"}\",\"result\":\"${if (result.coinIsHeads) "heads" else "tails"}\"}"
            )
            phase = "result"
        }
    }

    // Reset when switching away
    LaunchedEffect(Unit) { }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))

        // Coin visual — larger, with glow ring on result
        val coinBg = when (phase) {
            "result" -> if (flipResult?.isWin == true) TimeBetGreen.copy(alpha = 0.15f) else TimeBetRed.copy(alpha = 0.15f)
            "animating" -> flashColor.copy(alpha = 0.1f)
            else -> TimeBetSurfaceElevated
        }
        val coinBorderColor = when (phase) {
            "result" -> if (flipResult?.isWin == true) TimeBetGreen else TimeBetRed
            "animating" -> TimeBetGoldLight
            else -> TimeBetGoldLight.copy(alpha = 0.4f)
        }
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(coinBg)
                .border(3.dp, coinBorderColor, CircleShape)
                .graphicsLayer { rotationY = coinRotation.value; scaleX = coinScale.value; scaleY = coinScale.value },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(130.dp).clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(
                        TimeBetGoldLight.copy(alpha = 0.4f),
                        TimeBetGoldLight.copy(alpha = 0.08f)
                    )))
                    .border(2.dp, TimeBetGoldLight.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (phase) { "betting" -> if (betOnHeads) "H" else "T"; else -> if (showingHeads) "H" else "T" },
                    style = TimeBetTypography.displayLarge,
                    color = when { phase == "result" && flipResult?.isWin == true -> TimeBetGreen; phase == "result" && flipResult?.isWin == false -> TimeBetRed; else -> TimeBetGoldLight },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Result text inline
        if (phase == "result" && flipResult != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (flipResult!!.isWin) "+${TimeFormatter.formatMinutesSeconds(flipResult!!.profitSeconds)}" else "-${TimeFormatter.formatMinutesSeconds(flipResult!!.lossSeconds)}",
                style = TimeBetTypography.headlineMedium,
                color = if (flipResult!!.isWin) TimeBetGreen else TimeBetRed,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bet selector — always visible, disabled during animation
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = betOnHeads, onClick = { if (phase != "animating") betOnHeads = true },
                enabled = phase != "animating",
                label = { Text("Heads", color = if (betOnHeads) TimeBetBlack else TimeBetWhite) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, containerColor = TimeBetSurfaceElevated),
                shape = RoundedCornerShape(8.dp))
            Spacer(modifier = Modifier.width(16.dp))
            FilterChip(selected = !betOnHeads, onClick = { if (phase != "animating") betOnHeads = false },
                enabled = phase != "animating",
                label = { Text("Tails", color = if (!betOnHeads) TimeBetBlack else TimeBetWhite) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, containerColor = TimeBetSurfaceElevated),
                shape = RoundedCornerShape(8.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))

        InlineStakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })

        Spacer(modifier = Modifier.height(10.dp))

        // FLIP button — works in betting and result phases
        val canFlip = (phase == "betting" || phase == "result") && stakeSeconds in 1..balance && !isLocked
        Button(
            onClick = {
                if (phase == "result") { phase = "betting"; flipResult = null; flashColor = TimeBetSurfaceElevated }
                else executeFlip()
            },
            enabled = canFlip,
            colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                when { phase == "animating" -> "FLIPPING…"; phase == "result" -> "FLIP AGAIN"; else -> "FLIP" },
                style = TimeBetTypography.labelLarge
            )
        }
    }
}

// ─── Inline Mines ───

@Composable
private fun InlineMines(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var mineCount by remember { mutableIntStateOf(3) }
    var phase by remember { mutableStateOf("betting") }
    var cells by remember { mutableStateOf(List(25) { "hidden" }) }
    var revealedCount by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var winAmount by remember { mutableLongStateOf(0L) }
    var currentMultiplier by remember { mutableDoubleStateOf(1.0) }
    var minePositions by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var resultText by remember { mutableStateOf("") }
    var resultColor by remember { mutableStateOf(TimeBetWhite) }

    val engine = remember { ServiceLocator.minesEngine }

    fun startGame() {
        minePositions = ServiceLocator.timeBankRepository.generateMinePositions(mineCount)
        cells = List(25) { "hidden" }
        revealedCount = 0; gameOver = false; winAmount = 0L; currentMultiplier = 1.0
        resultText = ""; phase = "playing"
    }

    fun reveal(index: Int) {
        if (gameOver || cells[index] != "hidden") return
        if (index in minePositions) {
            val newCells = cells.toMutableList()
            newCells[index] = "mine"
            minePositions.forEach { if (newCells[it] == "hidden") newCells[it] = "mine_revealed" }
            cells = newCells
            gameOver = true
            scope.launch {
                ServiceLocator.timeBankRepository.settleCasinoRound(
                    gameType = "mines", stakeSeconds = stakeSeconds, isWin = false, profitSeconds = 0,
                    metadataJson = "{\"mines\":$mineCount,\"revealed\":$revealedCount,\"hit_mine\":true}")
                winAmount = 0L; resultText = "-${TimeFormatter.formatMinutesShort(stakeSeconds)}"; resultColor = TimeBetRed
            }
        } else {
            val newCells = cells.toMutableList(); newCells[index] = "safe"; cells = newCells
            revealedCount++
            currentMultiplier = engine.calculateMultiplier(mineCount, revealedCount)
            winAmount = engine.calculatePayout(stakeSeconds, mineCount, revealedCount)
        }
    }

    fun cashOut() {
        gameOver = true
        scope.launch {
            val profit = winAmount - stakeSeconds
            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "mines", stakeSeconds = stakeSeconds, isWin = profit > 0, profitSeconds = profit.coerceAtLeast(0),
                metadataJson = "{\"mines\":$mineCount,\"revealed\":$revealedCount,\"cashed_out\":true}")
            resultText = "+${TimeFormatter.formatMinutesSeconds(profit.coerceAtLeast(0))}"; resultColor = TimeBetGreen
        }
    }

    val playingOrDone = phase in listOf("playing")

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Stats row (visible during play and result) ──
        if (playingOrDone) {
            if (resultText.isNotEmpty()) {
                Text(resultText, style = TimeBetTypography.headlineMedium, color = resultColor, fontWeight = FontWeight.Bold)
            } else if (phase == "playing") {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.2fx", currentMultiplier), style = TimeBetTypography.labelLarge, color = TimeBetGreen, fontWeight = FontWeight.SemiBold)
                        Text("Multiplier", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(TimeFormatter.formatMinutesShort(winAmount), style = TimeBetTypography.labelLarge, color = TimeBetWhite, fontWeight = FontWeight.SemiBold)
                        Text("Payout", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${25 - mineCount - revealedCount}", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary, fontWeight = FontWeight.SemiBold)
                        Text("Safe Left", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── 5x5 Grid (always visible) ──
        LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.size(280.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(25) { index ->
                    val cellState = cells.getOrElse(index) { "hidden" }
                    val isMine = index in minePositions
                    val showBomb = cellState in listOf("mine", "mine_revealed")
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                            .background(when {
                                cellState == "mine" -> TimeBetRed.copy(alpha = 0.7f)
                                cellState == "mine_revealed" -> TimeBetRed.copy(alpha = 0.25f)
                                cellState == "safe" -> TimeBetGreen.copy(alpha = 0.18f)
                                else -> TimeBetSurfaceElevated
                            })
                            .border(1.dp, when {
                                cellState == "safe" -> TimeBetGreen.copy(alpha = 0.5f)
                                cellState == "mine" -> TimeBetRed.copy(alpha = 0.5f)
                                else -> TimeBetBorder
                            }, RoundedCornerShape(8.dp))
                            .clickable(enabled = cellState == "hidden" && !gameOver) { reveal(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            cellState == "mine" -> Text("💣", style = TimeBetTypography.headlineMedium)
                            cellState == "mine_revealed" -> Text("💣", style = TimeBetTypography.labelLarge)
                            cellState == "safe" -> {
                                // Glowing diamond gem
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .rotate(45f)
                                        .background(TimeBetGreen)
                                )
                            }
                            !gameOver -> {
                                // Unrevealed tile — diamond gem, brighter during play
                                Box(
                                    modifier = Modifier
                                        .size(if (phase == "playing") 16.dp else 10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .rotate(45f)
                                        .background(
                                            if (phase == "playing") TimeBetGoldLight.copy(alpha = 0.5f)
                                            else TimeBetTextSecondary.copy(alpha = 0.2f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

        // ── Controls below grid ──
        when {
            phase == "betting" -> {
                Text("Mines", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Text("$mineCount mines · ${25 - mineCount} safe", style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = mineCount.toFloat(), onValueChange = { mineCount = it.toInt().coerceIn(1, 24) },
                    valueRange = 1f..24f, steps = 22,
                    colors = SliderDefaults.colors(thumbColor = TimeBetWhite, activeTrackColor = TimeBetWhite, inactiveTrackColor = TimeBetBorder),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                InlineStakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { startGame() }, enabled = stakeSeconds in 1..balance && !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("START GAME", style = TimeBetTypography.labelLarge) }
            }
            phase == "playing" && !gameOver -> {
                Button(onClick = { cashOut() }, enabled = revealedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetGreen, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("CASH OUT · ${TimeFormatter.formatMinutesShort(winAmount)}", style = TimeBetTypography.labelLarge) }
            }
            gameOver || resultText.isNotEmpty() -> {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = { phase = "betting"; cells = List(25) { "hidden" }; revealedCount = 0; gameOver = false; winAmount = 0L; resultText = ""; minePositions = emptySet() },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("START", style = TimeBetTypography.labelLarge) }
            }
        }
    }
}

// ─── Inline Roulette ───

@Composable
private fun InlineRoulette(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var selectedBetType by remember { mutableStateOf<BetType?>(null) }
    var selectedNumber by remember { mutableIntStateOf(-1) }
    var selectedDozen by remember { mutableIntStateOf(-1) }
    var selectedColumn by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf("betting") }
    var spinResult by remember { mutableStateOf<RouletteSpinResult?>(null) }
    var betResult by remember { mutableStateOf<RouletteBetResult?>(null) }
    var displayNumber by remember { mutableIntStateOf(0) }
    val wheelRotation = remember { Animatable(0f) }

    fun buildBet(): RouletteBet? {
        val type = selectedBetType ?: return null
        return RouletteBet(type = type, stakeSeconds = stakeSeconds,
            numbers = if (selectedNumber >= 0) listOf(selectedNumber) else emptyList(),
            dozens = if (selectedDozen > 0) listOf(selectedDozen) else emptyList(),
            columns = if (selectedColumn > 0) listOf(selectedColumn) else emptyList())
    }

    /**
     * Maps a roulette number (0-36) to an angle on the wheel (in degrees).
     */
    fun numberToAngle(number: Int): Float {
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

            // Flashing numbers during spin (concurrent, slows down with deceleration)
            var flashSpeed = 50L
            val numberJob = scope.launch {
                var count = 0
                while (phase == "spinning") {
                    displayNumber = (0..36).random()
                    // Gradually slow down: 50ms → 250ms over the spin duration
                    flashSpeed = ((50 + count * 4).coerceAtMost(250)).toLong()
                    delay(flashSpeed)
                    count++
                }
            }

            // Smooth multi-phase wheel spin
            val numberAngle = numberToAngle(result.number)
            val spinCount = (5..8).random()
            val targetAngle = spinCount * 360f + (360f - numberAngle)

            wheelRotation.snapTo(0f)

            // Phase 1: Fast spin — 1400ms
            wheelRotation.animateTo(
                targetAngle * 0.7f,
                animationSpec = tween(1400, easing = LinearEasing)
            )

            // Phase 2: Gradual deceleration — 800ms
            wheelRotation.animateTo(
                targetAngle * 0.93f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )

            // Kill random flashing NOW — show result during spring settle
            numberJob.cancel()

            // Phase 3: Fine settle with spring physics — result already showing
            displayNumber = result.number
            wheelRotation.animateTo(
                targetAngle,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 70f)
            )

            val evaluated = ServiceLocator.timeBankRepository.evaluateRouletteBet(bet, result)
            betResult = evaluated
            ServiceLocator.timeBankRepository.settleCasinoRound(
                gameType = "roulette", stakeSeconds = stakeSeconds,
                isWin = evaluated.isWin, profitSeconds = evaluated.profitSeconds,
                metadataJson = "{\"number\":${result.number},\"bet_type\":\"${bet.type.name}\",\"payout\":${evaluated.payoutMultiplier}}"
            )
            phase = "result"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Roulette Wheel with colored segments + pointer ──
        val wheelSize = 140.dp
        Box(
            modifier = Modifier.size(wheelSize),
            contentAlignment = Alignment.Center
        ) {
            // Rotating wheel with colored segments
            Canvas(
                modifier = Modifier
                    .size(wheelSize)
                    .graphicsLayer { rotationZ = wheelRotation.value }
            ) {
                val wheelOrder = listOf(
                    0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
                    10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
                )
                val redNumbers = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
                val sweepAngle = 360f / 37f
                val strokeWidth = size.minDimension * 0.15f
                val outerRadius = size.minDimension / 2f - strokeWidth / 2f
                val topLeft = Offset(
                    (size.width - outerRadius * 2) / 2f,
                    (size.height - outerRadius * 2) / 2f
                )
                val arcSize = Size(outerRadius * 2, outerRadius * 2)

                // Draw colored segments around rim
                wheelOrder.forEachIndexed { index, num ->
                    val startAngle = index * sweepAngle - 90f
                    val color = when {
                        num == 0 -> android.graphics.Color.rgb(0, 92, 46) // Green
                        num in redNumbers -> android.graphics.Color.rgb(196, 30, 58) // Red
                        else -> android.graphics.Color.rgb(26, 26, 46) // Black
                    }
                    drawArc(
                        color = androidx.compose.ui.graphics.Color(color),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * 0.92f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                }

                // Inner dark circle
                val innerRadius = outerRadius - strokeWidth - 2f
                drawCircle(
                    color = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
                    radius = innerRadius,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }

            // Center number display (doesn't rotate)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (phase == "betting") "0" else "$displayNumber",
                    style = TimeBetTypography.displayMedium,
                    color = TimeBetWhite,
                    fontWeight = FontWeight.Bold
                )
                if (phase != "betting" && spinResult != null) {
                    val resultColor = when (spinResult!!.color) {
                        RouletteColor.RED -> TimeBetRed
                        RouletteColor.GREEN -> TimeBetGreen
                        else -> TimeBetTextSecondary
                    }
                    Text(
                        spinResult!!.color.name,
                        style = TimeBetTypography.labelSmall,
                        color = resultColor
                    )
                }
            }

            // Fixed pointer at top (doesn't rotate) — precision triangle
            val pointerColor = when (phase) {
                "result" -> when (spinResult?.color) {
                    RouletteColor.RED -> TimeBetRed
                    RouletteColor.GREEN -> TimeBetGreen
                    else -> TimeBetWhite
                }
                "spinning" -> TimeBetWhite
                else -> TimeBetGoldLight
            }
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-3).dp)
                    .size(14.dp, 10.dp)
            ) {
                val w = size.width
                val h = size.height
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w / 2, h)       // bottom point (aiming at wheel)
                    lineTo(0f, 0f)          // top-left
                    lineTo(w, 0f)           // top-right
                    close()
                }
                drawPath(path, pointerColor)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Result
        AnimatedVisibility(visible = phase == "result" && betResult != null,
            enter = fadeIn(spring()) + slideInVertically(spring()) { it / 2 }) {
            betResult?.let { br ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (br.isWin) "YOU WIN!" else "YOU LOSE", style = TimeBetTypography.headlineMedium,
                        color = if (br.isWin) TimeBetGreen else TimeBetRed, fontWeight = FontWeight.Bold)
                    Text(if (br.isWin) "+${TimeFormatter.formatMinutesSeconds(br.profitSeconds)}" else "-${TimeFormatter.formatMinutesSeconds(br.lossSeconds)}",
                        style = TimeBetTypography.labelLarge, color = if (br.isWin) TimeBetGreen else TimeBetRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (phase) {
            "betting" -> {
                val betTypes = listOf(
                    BetType.RED to "Red", BetType.BLACK to "Black",
                    BetType.ODD to "Odd", BetType.EVEN to "Even",
                    BetType.LOW to "1-18", BetType.HIGH to "19-36",
                    BetType.DOZEN to "Dozens", BetType.COLUMN to "Columns",
                    BetType.STRAIGHT to "Number"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
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
                Spacer(modifier = Modifier.height(4.dp))
                // Number grid for straight bets
                if (selectedBetType == BetType.STRAIGHT) {
                    LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items((0..36).toList()) { num ->
                            val bg = when {
                                num == 0 -> TimeBetGreen.copy(alpha = 0.5f)
                                num in ServiceLocator.rouletteEngine.redNumbers -> TimeBetRed.copy(alpha = 0.3f)
                                else -> TimeBetSurfaceElevated
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (selectedNumber == num) TimeBetWhite else bg)
                                .clickable { selectedNumber = num }.padding(6.dp), contentAlignment = Alignment.Center) {
                                Text("$num", style = TimeBetTypography.labelSmall, color = if (selectedNumber == num) TimeBetBlack else TimeBetWhite)
                            }
                        }
                    }
                }
                if (selectedBetType == BetType.DOZEN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1st 12" to 1, "2nd 12" to 2, "3rd 12" to 3).forEach { (label, d) ->
                            FilterChip(selected = selectedDozen == d, onClick = { selectedDozen = d }, label = { Text(label, style = TimeBetTypography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack, containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                                shape = RoundedCornerShape(6.dp))
                        }
                    }
                }
                if (selectedBetType == BetType.COLUMN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Col 1" to 1, "Col 2" to 2, "Col 3" to 3).forEach { (label, c) ->
                            FilterChip(selected = selectedColumn == c, onClick = { selectedColumn = c }, label = { Text(label, style = TimeBetTypography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack, containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                                shape = RoundedCornerShape(6.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                InlineStakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { spin() },
                    enabled = buildBet() != null && stakeSeconds in 1..balance && !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("SPIN", style = TimeBetTypography.labelLarge) }
            }
            "result" -> {
                Button(
                    onClick = { phase = "betting"; spinResult = null; betResult = null; displayNumber = 0 },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("SPIN AGAIN", style = TimeBetTypography.labelLarge) }
            }
        }
    }
}

// ─── Inline Blackjack ───

@Composable
private fun InlineBlackjack(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var phase by remember { mutableStateOf("betting") } // betting, playing, result
    var gameState by remember { mutableStateOf<BlackjackEngine.BlackjackState?>(null) }
    var resultText by remember { mutableStateOf("") }
    var resultColor by remember { mutableStateOf(TimeBetWhite) }
    var resultDetail by remember { mutableStateOf("") }

    fun settleRound(result: BlackjackEngine.BlackjackResult, totalStake: Long) {
        scope.launch {
            val isPush = result.outcome == BlackjackEngine.BlackjackOutcome.PUSH
            val isWin = result.outcome in setOf(BlackjackEngine.BlackjackOutcome.PLAYER_BLACKJACK, BlackjackEngine.BlackjackOutcome.DEALER_BUST, BlackjackEngine.BlackjackOutcome.PLAYER_WIN)
            if (!isPush) {
                val profit = when (result.outcome) {
                    BlackjackEngine.BlackjackOutcome.PLAYER_BLACKJACK -> ServiceLocator.timeBankRepository.blackjackProfit(totalStake)
                    BlackjackEngine.BlackjackOutcome.DEALER_BUST, BlackjackEngine.BlackjackOutcome.PLAYER_WIN -> totalStake
                    else -> 0L
                }
                ServiceLocator.timeBankRepository.settleCasinoRound(
                    gameType = "blackjack", stakeSeconds = totalStake, isWin = profit > 0, profitSeconds = profit,
                    metadataJson = "{\"player\":${result.playerValue},\"dealer\":${result.dealerValue},\"outcome\":\"${result.outcome.name}\"}")
                resultText = if (isWin) "+${TimeFormatter.formatMinutesSeconds(profit)}" else "-${TimeFormatter.formatMinutesSeconds(totalStake)}"
                resultColor = if (isWin) TimeBetGreen else TimeBetRed
            } else {
                resultText = "Push"
                resultColor = TimeBetAmber
            }
            resultDetail = "P: ${result.playerValue} · D: ${result.dealerValue}"
            phase = "result"
            // Auto-return to betting screen, but cards + result stay visible
            delay(2000)
            phase = "betting"
        }
    }

    var lastResultText by remember { mutableStateOf("") }
    var lastResultColor by remember { mutableStateOf(TimeBetWhite) }

    fun deal() {
        // Clear previous result before new deal
        lastResultText = resultText; lastResultColor = resultColor
        resultText = ""; resultDetail = ""
        val state = ServiceLocator.timeBankRepository.dealBlackjack().copy(stakeSeconds = stakeSeconds)
        gameState = state
        if (state.result != null) { settleRound(state.result!!, state.stakeSeconds); return }
        phase = "playing"
    }

    fun stand() {
        val state = gameState ?: return
        val newState = ServiceLocator.timeBankRepository.blackjackStand(state, state.stakeSeconds)
        gameState = newState
        if (newState.result != null) { settleRound(newState.result!!, newState.stakeSeconds) }
    }

    fun hit() {
        val state = gameState ?: return
        val newState = ServiceLocator.timeBankRepository.blackjackHit(state)
        gameState = newState
        if (newState.result != null) { settleRound(newState.result!!, newState.stakeSeconds) }
        else if (newState.playerHand.value >= 21) { stand() }
    }

    fun doubleDown() {
        val state = gameState ?: return
        val newState = ServiceLocator.timeBankRepository.blackjackDoubleDown(state, stakeSeconds)
        gameState = newState
        if (newState.result != null) { settleRound(newState.result!!, newState.stakeSeconds) }
    }

    val showCards = phase != "betting" || gameState != null  // also show when betting has old result
    val gameDone = phase == "result" || gameState?.isDealerDone == true || gameState?.isPlayerDone == true
    val hasOldResult = phase == "betting" && gameState != null && gameState?.isDealerDone == true

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Card area (always visible, shows backs before deal) ──
        // Dealer
        Text("Dealer", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showCards && gameState != null) {
                val state = gameState!!
                state.dealerHand.cards.forEachIndexed { i, card ->
                    InlineCardView(card = card, faceUp = i == 0 || state.isDealerDone || gameDone)
                }
            } else {
                // Show 2 face-down card backs before deal
                repeat(2) { InlineCardView(card = null, faceUp = false) }
            }
        }
        Text(
            when {
                !showCards -> "?"
                gameState?.isDealerDone == true || gameDone -> "${gameState?.dealerHand?.value ?: 0}"
                gameState != null -> "${gameState!!.dealerHand.cards.first().value}+?"
                else -> "?"
            },
            style = TimeBetTypography.labelLarge, color = TimeBetWhite
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = TimeBetBorder)
        Spacer(modifier = Modifier.height(8.dp))

        // Player
        Text("Your Hand", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showCards && gameState != null) {
                gameState!!.playerHand.cards.forEach { card -> InlineCardView(card = card, faceUp = true) }
            } else {
                repeat(2) { InlineCardView(card = null, faceUp = false) }
            }
        }
        Text(
            if (gameState != null) "${gameState!!.playerHand.value}${if (gameState!!.playerHand.isSoft) " (soft)" else ""}" else "?",
            style = TimeBetTypography.headlineMedium, color = TimeBetWhite
        )

        // Result text inline — shown during result AND persisted during betting
        if (resultText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(resultText, style = TimeBetTypography.headlineMedium, color = resultColor, fontWeight = FontWeight.Bold)
            if (resultDetail.isNotEmpty()) Text(resultDetail, style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Controls ──
        when {
            phase == "betting" -> {
                if (!hasOldResult) {
                    Text("Blackjack", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                    Text("Dealer stands on soft 17 · Blackjack pays 3:2", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                InlineStakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { gameState = null; deal() }, enabled = stakeSeconds in 1..balance && !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("DEAL", style = TimeBetTypography.labelLarge) }
            }
            phase == "playing" -> {
                val state = gameState
                if (state != null && !state.isPlayerDone && state.result == null && state.playerHand.value < 21) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { hit() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TimeBetBorderLight))
                        ) { Text("Hit", color = TimeBetWhite) }
                        OutlinedButton(onClick = { stand() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TimeBetBorderLight))
                        ) { Text("Stand", color = TimeBetWhite) }
                        if (state.canDoubleDown && balance >= stakeSeconds * 2) {
                            OutlinedButton(onClick = { doubleDown() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TimeBetGreen))
                            ) { Text("2x", color = TimeBetGreen) }
                        }
                    }
                }
            }
            phase == "result" -> {
                // Auto-returning; brief loading indicator
                Spacer(modifier = Modifier.height(4.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TimeBetTextTertiary, strokeWidth = 2.dp)
            }
        }
    }
}

// ─── Inline Crash ───

@Composable
private fun InlineCrash(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var phase by remember { mutableStateOf("betting") }
    var currentMultiplier by remember { mutableDoubleStateOf(1.00) }
    var crashPoint by remember { mutableDoubleStateOf(1.00) }
    var hasCashedOut by remember { mutableStateOf(false) }
    var payoutSeconds by remember { mutableLongStateOf(0L) }
    var isCrashed by remember { mutableStateOf(false) }
    var autoCashoutAt by remember { mutableDoubleStateOf(2.00) }
    var autoCashoutEnabled by remember { mutableStateOf(false) }
    var customAutoText by remember { mutableStateOf("") }
    var isCustomAuto by remember { mutableStateOf(false) }

    fun startGame() {
        crashPoint = ServiceLocator.timeBankRepository.generateCrashPoint()
        currentMultiplier = 1.00; isCrashed = false; hasCashedOut = false; payoutSeconds = 0L
        phase = "running"

        scope.launch {
            val startTime = System.currentTimeMillis()
            while (!isCrashed && !hasCashedOut) {
                val elapsed = System.currentTimeMillis() - startTime
                currentMultiplier = ServiceLocator.timeBankRepository.crashMultiplierAtTime(elapsed, crashPoint)
                // Auto cashout check
                if (autoCashoutEnabled && currentMultiplier >= autoCashoutAt && !isCrashed) {
                    hasCashedOut = true
                    val profit = ServiceLocator.timeBankRepository.crashPayout(stakeSeconds, currentMultiplier)
                    payoutSeconds = profit
                    ServiceLocator.timeBankRepository.settleCasinoRound(
                        gameType = "crash", stakeSeconds = stakeSeconds, isWin = profit > 0, profitSeconds = profit.coerceAtLeast(0),
                        metadataJson = "{\"crash\":${String.format("%.2f", crashPoint)},\"cashed_out\":true,\"auto\":true,\"at\":${String.format("%.2f", currentMultiplier)}}")
                    phase = "cashed_out"
                }
                if (currentMultiplier >= crashPoint && phase == "running" && !hasCashedOut) {
                    isCrashed = true
                    ServiceLocator.timeBankRepository.settleCasinoRound(
                        gameType = "crash", stakeSeconds = stakeSeconds, isWin = false, profitSeconds = 0,
                        metadataJson = "{\"crash\":${String.format("%.2f", crashPoint)},\"cashed_out\":false}")
                    phase = "crashed"
                }
                delay(50)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (phase) {
            "betting" -> {
                // Animated rocket emoji
                val rocketBob = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        rocketBob.animateTo(-12f, animationSpec = tween(600, easing = FastOutSlowInEasing))
                        rocketBob.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
                    }
                }
                Text(
                    "🚀",
                    style = TimeBetTypography.displayLarge,
                    modifier = Modifier.offset(y = rocketBob.value.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Crash", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Text("Cash out before it crashes", style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                // Auto cashout — presets row
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.width(6.dp))
                    listOf(1.5, 2.0, 3.0, 5.0).forEach { mult ->
                        FilterChip(
                            selected = autoCashoutEnabled && !isCustomAuto && autoCashoutAt == mult,
                            onClick = { autoCashoutEnabled = true; autoCashoutAt = mult; isCustomAuto = false },
                            label = { Text("${mult}x", style = TimeBetTypography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TimeBetGreen, selectedLabelColor = TimeBetBlack,
                                containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    // Custom input with "x" label
                    Box(
                        modifier = Modifier
                            .width(56.dp).height(30.dp)
                            .background(
                                if (isCustomAuto) TimeBetGreen.copy(alpha = 0.15f) else TimeBetSurface,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = customAutoText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                if (filtered.length <= 5) {
                                    customAutoText = filtered
                                    val parsed = filtered.toDoubleOrNull()
                                    if (parsed != null && parsed >= 1.01) {
                                        autoCashoutAt = parsed
                                        autoCashoutEnabled = true
                                        isCustomAuto = true
                                    } else if (filtered.isEmpty()) {
                                        isCustomAuto = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TimeBetTypography.labelSmall.copy(
                                color = if (isCustomAuto) TimeBetGreen else TimeBetTextTertiary,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(TimeBetWhite),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (customAutoText.isEmpty()) {
                                        Text("x", style = TimeBetTypography.labelSmall,
                                            color = TimeBetTextTertiary.copy(alpha = 0.4f))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
                // Active auto-cashout indicator + OFF
                if (autoCashoutEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "✓ ${String.format("%.2f", autoCashoutAt)}x",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "OFF",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetRed,
                            modifier = Modifier.clickable {
                                autoCashoutEnabled = false; isCustomAuto = false; customAutoText = ""
                            }.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                InlineStakeSelector(balance = balance, stake = stakeSeconds, onStakeChange = { stakeSeconds = it })
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { startGame() }, enabled = stakeSeconds in 1..balance && !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("START", style = TimeBetTypography.labelLarge) }
            }
            "running" -> {
                val multColor = when { currentMultiplier >= 10 -> TimeBetRed; currentMultiplier >= 5 -> TimeBetAmber; currentMultiplier >= 2 -> TimeBetGreen; else -> TimeBetWhite }
                Text(String.format("%.2fx", currentMultiplier), style = TimeBetTypography.displayLarge, color = multColor, fontWeight = FontWeight.Bold)
                val currentPayout = ServiceLocator.timeBankRepository.crashPayout(stakeSeconds, currentMultiplier)
                val totalPayout = stakeSeconds + currentPayout
                Text(TimeFormatter.formatMinutesSeconds(currentPayout),
                    style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
                if (autoCashoutEnabled) Text("Auto @${autoCashoutAt}x", style = TimeBetTypography.labelSmall, color = TimeBetAmber)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    if (phase != "running") return@Button
                    hasCashedOut = true
                    payoutSeconds = currentPayout
                    scope.launch {
                        ServiceLocator.timeBankRepository.settleCasinoRound(
                            gameType = "crash", stakeSeconds = stakeSeconds, isWin = currentPayout > 0, profitSeconds = currentPayout.coerceAtLeast(0),
                            metadataJson = "{\"crash\":${String.format("%.2f", crashPoint)},\"cashed_out\":true,\"at\":${String.format("%.2f", currentMultiplier)}}")
                    }
                    phase = "cashed_out"
                },
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetGreen, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("CASH OUT · ${TimeFormatter.formatMinutesSeconds(totalPayout)}", style = TimeBetTypography.labelLarge) }
            }
            "crashed" -> {
                Text("💥 CRASHED!", style = TimeBetTypography.displayMedium, color = TimeBetRed, fontWeight = FontWeight.Bold)
                Text("@${String.format("%.2fx", crashPoint)}x", style = TimeBetTypography.headlineMedium, color = TimeBetRed)
                Spacer(modifier = Modifier.height(4.dp))
                Text("-${TimeFormatter.formatMinutesSeconds(stakeSeconds)}", style = TimeBetTypography.headlineMedium, color = TimeBetRed)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { phase = "betting"; isCrashed = false; hasCashedOut = false; payoutSeconds = 0L },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("START AGAIN", style = TimeBetTypography.labelLarge) }
            }
            "cashed_out" -> {
                Text("✅ CASHED OUT!", style = TimeBetTypography.displayMedium, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                Text("@${String.format("%.2fx", currentMultiplier)}x", style = TimeBetTypography.headlineMedium, color = TimeBetGreen)
                Spacer(modifier = Modifier.height(4.dp))
                Text("+${TimeFormatter.formatMinutesSeconds(payoutSeconds.coerceAtLeast(0))}", style = TimeBetTypography.headlineMedium, color = TimeBetGreen)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Crashed at ${String.format("%.2fx", crashPoint)}x", style = TimeBetTypography.bodyMedium, color = TimeBetRed)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { phase = "betting"; isCrashed = false; hasCashedOut = false; payoutSeconds = 0L },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("START AGAIN", style = TimeBetTypography.labelLarge) }
            }
        }
    }
}

// ─── Inline Baccarat ───

@Composable
private fun InlineBaccarat(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var betOn by remember { mutableStateOf("player") }
    var phase by remember { mutableStateOf("betting") }
    var result by remember { mutableStateOf<BaccaratEngine.BaccaratResult?>(null) }
    var profit by remember { mutableLongStateOf(0L) }
    var isWin by remember { mutableStateOf(false) }
    var faceDown by remember { mutableStateOf(true) }

    // Card flip animation
    val flipProgress = remember { Animatable(0f) }
    val revealProgress = remember { Animatable(0f) }

    fun deal() {
        val r = ServiceLocator.timeBankRepository.dealBaccarat()
        result = r
        faceDown = true
        phase = "dealing"
        scope.launch {
            flipProgress.snapTo(0f)
            revealProgress.snapTo(0f)
            // Card reveal animation: 1s flip
            flipProgress.animateTo(1f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
            faceDown = false
            revealProgress.animateTo(1f, animationSpec = tween(300))
            isWin = ServiceLocator.timeBankRepository.baccaratIsWin(r.outcome, betOn)
            profit = ServiceLocator.timeBankRepository.baccaratPayout(stakeSeconds, r.outcome, betOn)
            ServiceLocator.timeBankRepository.settleCasinoRound(
                "baccarat", stakeSeconds, isWin, profit,
                "{\"player\":${r.playerHand.total},\"banker\":${r.bankerHand.total},\"bet\":\"$betOn\"}"
            )
            phase = "result"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // Front screen — table felt design when no result
        if (result == null) {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .background(TimeBetGreen.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .border(1.dp, TimeBetGoldLight.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) {
                            Box(Modifier.size(36.dp, 50.dp).background(TimeBetBorderLight, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                Text("🂠", style = TimeBetTypography.headlineMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("BACCARAT", style = TimeBetTypography.labelMedium, color = TimeBetGoldLight.copy(alpha = 0.5f),
                        letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp))
                }
            }
        }

        // Cards area
        if (result != null) {
            val r = result!!
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("PLAYER" to r.playerHand, "BANKER" to r.bankerHand).forEach { (label, hand) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .background(TimeBetSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, TimeBetBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    hand.cards.forEachIndexed { i, c ->
                                        Box(
                                            Modifier
                                                .size(42.dp, 60.dp)
                                                .background(
                                                    if (faceDown) TimeBetBorderLight else TimeBetSurfaceElevated,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .border(1.dp, if (!faceDown && i == hand.cards.size - 1) TimeBetGoldLight.copy(alpha = 0.4f) else TimeBetBorder, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!faceDown) {
                                                val isFace = c.displayRank in listOf("A", "J", "Q", "K")
                                                Text(c.displayRank, style = TimeBetTypography.labelLarge,
                                                    color = if (isFace) TimeBetGoldLight else TimeBetWhite)
                                            } else {
                                                Text("?", style = TimeBetTypography.labelLarge, color = TimeBetTextTertiary)
                                            }
                                        }
                                    }
                                }
                                if (!faceDown) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("${hand.total}", style = TimeBetTypography.headlineMedium, color = TimeBetWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Result display
        AnimatedVisibility(phase == "result", enter = fadeIn(spring()) + scaleIn(spring())) {
            result?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when { isWin && betOn == "tie" -> "🎯 TIE WIN!"; isWin -> "✅ YOU WIN!"; it.outcome == BaccaratEngine.Outcome.TIE -> "🤝 PUSH"; else -> "❌ YOU LOSE" },
                        style = TimeBetTypography.headlineMedium,
                        color = when { isWin -> TimeBetGreen; it.outcome == BaccaratEngine.Outcome.TIE -> TimeBetAmber; else -> TimeBetRed },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isWin) "+${TimeFormatter.formatMinutesSeconds(profit)}"
                        else if (it.outcome == BaccaratEngine.Outcome.TIE && betOn != "tie") "±0"
                        else "-${TimeFormatter.formatMinutesSeconds(stakeSeconds)}",
                        style = TimeBetTypography.labelLarge,
                        color = if (isWin) TimeBetGreen else TimeBetRed
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Betting controls
        if (phase != "dealing") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("player" to "PLAYER", "tie" to "TIE 9x", "banker" to "BANKER").forEach { (k, l) ->
                    FilterChip(selected = betOn == k, onClick = { if (phase == "betting") betOn = k },
                        label = { Text(l, style = TimeBetTypography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TimeBetWhite, selectedLabelColor = TimeBetBlack,
                            containerColor = TimeBetSurfaceElevated, labelColor = TimeBetWhite),
                        shape = RoundedCornerShape(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            InlineStakeSelector(balance, stakeSeconds) { stakeSeconds = it }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                    if (phase == "result") { phase = "betting"; result = null }
                    else deal()
                }, enabled = (phase == "betting" || phase == "result") && stakeSeconds in 1..balance && !isLocked,
                colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (phase == "result") "DEAL AGAIN" else "DEAL", style = TimeBetTypography.labelLarge) }
        }
    }
}

// ─── Inline Chicken ───

@Composable
private fun InlineChicken(balance: Long, isLocked: Boolean) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var totalLanes by remember { mutableIntStateOf(6) }
    var phase by remember { mutableStateOf("betting") }
    var gameState by remember { mutableStateOf<ChickenEngine.ChickenGameState?>(null) }
    var profit by remember { mutableLongStateOf(0L) }

    fun startGame() {
        gameState = ServiceLocator.timeBankRepository.createChickenGame(totalLanes, stakeSeconds)
        phase = "crossing"
    }

    fun crossNext() {
        val state = gameState ?: return
        scope.launch {
            delay(300)
            val next = ServiceLocator.timeBankRepository.tryCrossLane(state)
            gameState = next
            if (next.isCrashed) {
                ServiceLocator.timeBankRepository.settleCasinoRound("chicken", stakeSeconds, false, 0,
                    "{\"lanes\":${next.totalLanes},\"crossed\":${next.lanesCrossed}}")
                phase = "crashed"
            }
        }
    }

    fun cashOut() {
        val state = gameState ?: return
        val cashed = ServiceLocator.timeBankRepository.cashOutChicken(state, stakeSeconds)
        gameState = cashed; profit = cashed.profitSeconds
        scope.launch {
            ServiceLocator.timeBankRepository.settleCasinoRound("chicken", stakeSeconds, true, profit,
                "{\"lanes\":${cashed.totalLanes},\"crossed\":${cashed.lanesCrossed}}")
            phase = "cashed_out"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Simplified road visual
        val state = gameState
        Box(Modifier.weight(1f).fillMaxWidth().background(TimeBetSurface, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (state != null && phase != "betting") {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    for (i in state.totalLanes downTo 1) {
                        val isCrossed = i <= state.lanesCrossed
                        val isCurrent = i == state.lanesCrossed + 1 && state.isActive
                        val isCrashedHere = i == state.lanesCrossed && !state.isActive && !state.hasCashedOut
                        Row(Modifier.fillMaxWidth().height(36.dp).background(
                            when { isCrashedHere -> TimeBetRed.copy(alpha = 0.3f); isCrossed -> TimeBetGreen.copy(alpha = 0.15f); else -> TimeBetBlack },
                            RoundedCornerShape(4.dp)
                        ), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            if (isCurrent) Text("🐔", style = TimeBetTypography.headlineMedium)
                            else if (isCrashedHere) Text("💥", style = TimeBetTypography.headlineMedium)
                            else if (isCrossed) Text("✓", style = TimeBetTypography.labelSmall, color = TimeBetGreen)
                            else Text("🚗", style = TimeBetTypography.labelSmall)
                        }
                    }
                }
            } else {
                Text("🐔", style = TimeBetTypography.displayLarge)
            }
        }

        Spacer(Modifier.height(12.dp))

        when (phase) {
            "betting" -> {
                Text("Lanes: $totalLanes", style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
                Slider(value = totalLanes.toFloat(), onValueChange = { totalLanes = it.toInt().coerceIn(4, 10) }, valueRange = 4f..10f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = TimeBetWhite, activeTrackColor = TimeBetWhite, inactiveTrackColor = TimeBetBorder))
                Spacer(Modifier.height(8.dp))
                InlineStakeSelector(balance, stakeSeconds) { stakeSeconds = it }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { startGame() }, enabled = stakeSeconds in 1..balance && !isLocked,
                    colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("CROSS ROAD", style = TimeBetTypography.labelLarge) }
            }
            "crossing" -> {
                val s = gameState!!
                val mult = ChickenEngine.calculateMultiplier(s.lanesCrossed, s.totalLanes)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.lanesCrossed}/${s.totalLanes}", style = TimeBetTypography.headlineMedium, color = TimeBetWhite, fontWeight = FontWeight.Bold)
                        Text("Lanes", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.2fx", mult), style = TimeBetTypography.headlineMedium, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                        Text("Multiplier", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { crossNext() }, colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("CROSS", style = TimeBetTypography.labelLarge) }
                    if (s.lanesCrossed > 0) {
                        Button(onClick = { cashOut() }, colors = ButtonDefaults.buttonColors(TimeBetGreen, TimeBetBlack),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("CASH OUT", style = TimeBetTypography.labelLarge) }
                    }
                }
            }
            "crashed" -> {
                Text("💥 SPLAT!", style = TimeBetTypography.displayMedium, color = TimeBetRed, fontWeight = FontWeight.Bold)
                Text("-${TimeFormatter.formatMinutesSeconds(stakeSeconds)}", style = TimeBetTypography.headlineMedium, color = TimeBetRed)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { phase = "betting"; gameState = null; profit = 0L },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("CROSS AGAIN", style = TimeBetTypography.labelLarge) }
            }
            "cashed_out" -> {
                Text("🐔 SAFE!", style = TimeBetTypography.displayMedium, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                Text("+${TimeFormatter.formatMinutesSeconds(profit)}", style = TimeBetTypography.headlineMedium, color = TimeBetGreen)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { phase = "betting"; gameState = null; profit = 0L },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(TimeBetWhite, TimeBetBlack),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("CROSS AGAIN", style = TimeBetTypography.labelLarge) }
            }
        }
    }
}

// ─── Shared Inline Components ───

@Composable
private fun InlineStakeSelector(balance: Long, stake: Long, onStakeChange: (Long) -> Unit) {
    val maxStake = (balance * com.timebet.app.util.TimeBetConstants.MAX_STAKE_PERCENTAGE).toLong().coerceAtLeast(60L)
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Stake", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            if (isEditing) {
                // Minimal inline edit
                BasicTextField(
                    value = editText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it in "ms: " }
                        editText = filtered
                        val seconds = parseStakeText(filtered)
                        if (seconds > 0) onStakeChange(seconds.coerceIn(60, maxStake))
                    },
                    modifier = Modifier.width(100.dp).background(TimeBetSurface, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    textStyle = TimeBetTypography.labelLarge.copy(color = TimeBetWhite, textAlign = TextAlign.End),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(TimeBetWhite)
                )
            } else {
                Text(
                    TimeFormatter.formatMinutesSeconds(stake),
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetWhite,
                    modifier = Modifier.clickable {
                        editText = formatStakeText(stake); isEditing = true
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.timebet.app.util.TimeBetConstants.QUICK_STAKES_SECONDS.forEach { quickStake ->
                val enabled = quickStake <= maxStake
                FilterChip(
                    selected = stake == quickStake,
                    onClick = { if (enabled) { onStakeChange(quickStake); isEditing = false } },
                    label = { Text(TimeFormatter.formatMinutesShort(quickStake), style = TimeBetTypography.labelSmall,
                        color = when { stake == quickStake -> TimeBetBlack; enabled -> TimeBetWhite; else -> TimeBetTextTertiary }) },
                    enabled = enabled,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TimeBetWhite, containerColor = TimeBetSurfaceElevated),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val newVal = (stake - 60).coerceAtLeast(60); onStakeChange(newVal); isEditing = false
            }, enabled = stake > 60) {
                Icon(Icons.Filled.Remove, "Less", tint = if (stake > 60) TimeBetWhite else TimeBetTextTertiary)
            }
            Text(TimeFormatter.formatMinutesSeconds(stake), style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            IconButton(onClick = {
                val newVal = (stake + 60).coerceAtMost(maxStake); onStakeChange(newVal); isEditing = false
            }, enabled = stake < maxStake) {
                Icon(Icons.Filled.Add, "More", tint = if (stake < maxStake) TimeBetWhite else TimeBetTextTertiary)
            }
        }
    }
}

private fun formatStakeText(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
}

private fun parseStakeText(input: String): Long {
    val trimmed = input.trim().lowercase()
    if (":" in trimmed) {
        val parts = trimmed.split(":")
        val mins = parts.getOrNull(0)?.toLongOrNull() ?: 0
        val secs = parts.getOrNull(1)?.toLongOrNull() ?: 0
        return mins * 60 + secs
    }
    var total = 0L
    Regex("(\\d+)\\s*m").find(trimmed)?.let { total += (it.groupValues[1].toLongOrNull() ?: 0) * 60 }
    Regex("(\\d+)\\s*s").find(trimmed)?.let { total += (it.groupValues[1].toLongOrNull() ?: 0) }
    if (total > 0) return total
    trimmed.filter { it.isDigit() }.toLongOrNull()?.let { if (it > 0) return it * 60 }
    return -1
}

@Composable
private fun InlineCardView(card: BlackjackEngine.Card?, faceUp: Boolean) {
    val bg = if (faceUp && card != null) TimeBetSurfaceElevated else TimeBetSurface
    val borderColor = if (faceUp && card != null) TimeBetBorderLight else TimeBetBorder
    Box(
        modifier = Modifier.size(44.dp, 62.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (faceUp && card != null) {
            Text(card.displayRank, style = TimeBetTypography.labelLarge, color = TimeBetWhite)
        } else {
            // Card back pattern
            Text("🂠", style = TimeBetTypography.headlineMedium)
        }
    }
}

@Composable
private fun CasinoStatChip(label: String, value: String, modifier: Modifier = Modifier, isPositive: Boolean? = null) {
    Box(modifier = modifier.background(TimeBetSurfaceElevated, RoundedCornerShape(8.dp)).padding(12.dp)) {
        Column {
            Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = TimeBetTypography.labelLarge,
                color = when (isPositive) { true -> TimeBetGreen; false -> TimeBetRed; null -> TimeBetWhite })
        }
    }
}
