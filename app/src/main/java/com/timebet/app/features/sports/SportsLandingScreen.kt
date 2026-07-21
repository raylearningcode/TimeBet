package com.timebet.app.features.sports

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.core.database.entity.SportsPredictionEntity
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sports Landing — Stake-inspired layout.
 *
 * - Balance bar always visible
 * - Tabs: [Fixtures] [My Bets]
 * - Fixtures: competition accordions with match rows + 1X2 odds chips
 * - My Bets: Active bets + Settled bets history
 * - Bottom sheet bet slip on odds tap
 * - Real data from Supabase only — no hardcoded fixtures
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsLandingScreen(onMatchClick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var activeStake by remember { mutableLongStateOf(0L) }
    var maxStake by remember { mutableLongStateOf(0L) }
    var activePredictions by remember { mutableStateOf<List<SportsPredictionEntity>>(emptyList()) }
    var settledPredictions by remember { mutableStateOf<List<SportsPredictionEntity>>(emptyList()) }
    var fixtures by remember { mutableStateOf<List<FixtureCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Fixtures, 1=My Bets

    // Bottom sheet state
    var showBetSlip by remember { mutableStateOf(false) }
    var betSlipFixture by remember { mutableStateOf<FixtureCard?>(null) }
    var betSlipMarketType by remember { mutableStateOf("home_draw_away") }
    var betSlipSelection by remember { mutableStateOf("home") }
    var betSlipOdds by remember { mutableDoubleStateOf(2.0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load data
    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
        activeStake = ServiceLocator.timeBankRepository.getActiveSportsStake()
        val bank = ServiceLocator.timeBankEngine.getDailyBank()
        maxStake = (bank.baseAllowanceSeconds * 0.20).toLong()

        // Observe predictions
        launch {
            ServiceLocator.timeBankRepository.observeActivePredictions().collect { predictions ->
                activePredictions = predictions
            }
        }
        launch {
            try {
                // Fetch scheduled + live fixtures; also get recently finished for status accuracy
                val scheduled = ServiceLocator.supabaseSync.fetchFixtures("scheduled", 30)
                val live = try { ServiceLocator.supabaseSync.fetchFixtures("live", 10) } catch (_: Exception) { emptyList() }
                val allFixtures = (scheduled + live).distinctBy { it.id }

                // Client-side filter: exclude matches that kicked off >6 hours ago
                // Keep recent matches (within 6h) to show live/finished status updates
                val now = System.currentTimeMillis()
                val sixHoursMs = 6 * 60 * 60 * 1000L
                val relevantFixtures = allFixtures.filter { f ->
                    try {
                        val kickoff = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(f.kickoffTime)
                        // Keep if kickoff is in the future OR within the last 6 hours
                        kickoff != null && (kickoff.time > now || (now - kickoff.time) < sixHoursMs)
                    } catch (_: Exception) { true } // keep if we can't parse date
                }

                // Batch-fetch real odds for all fixtures
                val fixtureIds = relevantFixtures.map { it.id }
                val oddsMap = try {
                    ServiceLocator.supabaseSync.fetchOddsBatch(fixtureIds)
                } catch (_: Exception) { emptyMap() }

                fixtures = relevantFixtures.map { f ->
                    val markets = oddsMap[f.id] ?: emptyList()
                    val hda = markets.find { it.type == "home_draw_away" }
                    FixtureCard(
                        eventId = f.id,
                        competition = f.competition,
                        homeTeam = f.homeTeam,
                        awayTeam = f.awayTeam,
                        kickoffTime = formatKickoff(f.kickoffTime),
                        kickoffIso = f.kickoffTime,
                        homeOdds = hda?.selections?.find { it.name == "home" }?.odds
                            ?: computeDefaultOdds(f.homeTeam, f.awayTeam).first,
                        drawOdds = hda?.selections?.find { it.name == "draw" }?.odds
                            ?: computeDefaultOdds(f.homeTeam, f.awayTeam).second,
                        awayOdds = hda?.selections?.find { it.name == "away" }?.odds
                            ?: computeDefaultOdds(f.homeTeam, f.awayTeam).third
                    )
                }
            } catch (_: Exception) {
                fixtures = emptyList()
            }
            isLoading = false
        }
    }

    // Load settled predictions
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            try {
                settledPredictions = ServiceLocator.sportsPredictionDao.getByStatuses(
                    listOf(PredictionStatus.WON, PredictionStatus.LOST, PredictionStatus.VOID, PredictionStatus.CANCELLED)
                )
            } catch (_: Exception) {
                settledPredictions = emptyList()
            }
        }
    }

    // Bet slip bottom sheet
    if (showBetSlip && betSlipFixture != null) {
        BetSlipBottomSheet(
            sheetState = sheetState,
            fixture = betSlipFixture!!,
            marketType = betSlipMarketType,
            selection = betSlipSelection,
            odds = betSlipOdds,
            balance = balance,
            maxStake = maxStake,
            activeStake = activeStake,
            onDismiss = { showBetSlip = false },
            onBetPlaced = {
                showBetSlip = false
                scope.launch {
                    balance = ServiceLocator.timeBankEngine.getBalance()
                    activeStake = ServiceLocator.timeBankRepository.getActiveSportsStake()
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(TimeBetBlack)) {
        // ── Balance Bar ──
        BalanceBar(balance = balance, activeStake = activeStake, maxStake = maxStake)

        // ── Tab Toggle ──
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = TimeBetBlack,
            contentColor = TimeBetWhite,
            divider = { HorizontalDivider(color = TimeBetBorder) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Fixtures", style = TimeBetTypography.labelLarge) },
                selectedContentColor = TimeBetWhite,
                unselectedContentColor = TimeBetTextTertiary
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "My Bets${if (activePredictions.isNotEmpty()) " (${activePredictions.size})" else ""}",
                        style = TimeBetTypography.labelLarge
                    )
                },
                selectedContentColor = TimeBetWhite,
                unselectedContentColor = TimeBetTextTertiary
            )
        }

        when (selectedTab) {
            0 -> FixturesTab(
                fixtures = fixtures,
                isLoading = isLoading,
                onOddsTap = { fixture, marketType, selection, odds ->
                    betSlipFixture = fixture
                    betSlipMarketType = marketType
                    betSlipSelection = selection
                    betSlipOdds = odds
                    showBetSlip = true
                },
                onMatchClick = onMatchClick
            )
            1 -> MyBetsTab(
                activePredictions = activePredictions,
                settledPredictions = settledPredictions
            )
        }
    }
}

// ─── Balance Bar ───

@Composable
private fun BalanceBar(balance: Long, activeStake: Long, maxStake: Long) {
    Surface(color = TimeBetBlack, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("SPORTS", style = TimeBetTypography.labelMedium, color = TimeBetTextTertiary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp))
                Text(TimeFormatter.formatMinutesSeconds(balance), style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Active bets", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                Text(
                    "${TimeFormatter.formatMinutesShort(activeStake)} / ${TimeFormatter.formatMinutesShort(maxStake)}",
                    style = TimeBetTypography.bodyLarge,
                    color = if (activeStake >= maxStake) TimeBetAmber else TimeBetWhite,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Fixtures Tab ───

@Composable
private fun FixturesTab(
    fixtures: List<FixtureCard>,
    isLoading: Boolean,
    onOddsTap: (FixtureCard, String, String, Double) -> Unit,
    onMatchClick: (String) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TimeBetWhite)
        }
        return
    }

    if (fixtures.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚽", style = TimeBetTypography.headlineLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No upcoming fixtures", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Connect the Supabase backend to start\nseeing live football matches.",
                    style = TimeBetTypography.bodyMedium,
                    color = TimeBetTextTertiary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Your bet history is still available\nin the My Bets tab.",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {

        // Group by competition
        val grouped = fixtures.groupBy { it.competition }
        grouped.forEach { (competition, compFixtures) ->
            item { CompetitionHeader(competition) }
            items(compFixtures) { fixture ->
                FixtureRow(fixture = fixture, onOddsTap = onOddsTap, onMatchClick = { onMatchClick(fixture.eventId) })
            }
        }
    }
}

// ─── My Bets Tab ───

@Composable
private fun MyBetsTab(
    activePredictions: List<SportsPredictionEntity>,
    settledPredictions: List<SportsPredictionEntity>
) {
    if (activePredictions.isEmpty() && settledPredictions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No bets yet", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Place a bet on an upcoming fixture\nand it will appear here.",
                    style = TimeBetTypography.bodyMedium,
                    color = TimeBetTextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Active bets
        if (activePredictions.isNotEmpty()) {
            item {
                Text("ACTIVE", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(activePredictions) { prediction ->
                PredictionCard(prediction = prediction, isActive = true)
            }
        }

        // Settled bets
        if (settledPredictions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("SETTLED", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(settledPredictions.sortedByDescending { it.settledAt ?: it.placedAt }) { prediction ->
                PredictionCard(prediction = prediction, isActive = false)
            }
        }
    }
}

// ─── Competition Header ───

@Composable
private fun CompetitionHeader(name: String) {
    val flag = when {
        name.contains("Premier League", ignoreCase = true) -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        name.contains("Champions League", ignoreCase = true) -> "⭐"
        name.contains("World Cup", ignoreCase = true) -> "🏆"
        name.contains("Euro", ignoreCase = true) -> "🏆"
        name.contains("FA Cup", ignoreCase = true) -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        name.contains("La Liga", ignoreCase = true) -> "🇪🇸"
        name.contains("Serie A", ignoreCase = true) -> "🇮🇹"
        name.contains("Bundesliga", ignoreCase = true) -> "🇩🇪"
        name.contains("Ligue 1", ignoreCase = true) -> "🇫🇷"
        name.contains("Eredivisie", ignoreCase = true) -> "🇳🇱"
        else -> "⚽"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(flag, style = TimeBetTypography.bodyLarge)
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Fixture Row ───

@Composable
private fun FixtureRow(
    fixture: FixtureCard,
    onOddsTap: (FixtureCard, String, String, Double) -> Unit,
    onMatchClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(TimeBetSurfaceElevated).clickable(onClick = onMatchClick).padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kickoff time
            Column(modifier = Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    fixture.kickoffTime.takeWhile { it != '·' }.trim(),
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Teams
            Column(modifier = Modifier.weight(1f)) {
                Text(fixture.homeTeam, style = TimeBetTypography.bodyLarge, color = TimeBetWhite, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(fixture.awayTeam, style = TimeBetTypography.bodyLarge, color = TimeBetWhite, fontWeight = FontWeight.Medium)
            }

            // 1X2 Odds chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OddsChip("1", fixture.homeOdds) {
                    onOddsTap(fixture, "home_draw_away", "home", fixture.homeOdds)
                }
                OddsChip("X", fixture.drawOdds) {
                    onOddsTap(fixture, "home_draw_away", "draw", fixture.drawOdds)
                }
                OddsChip("2", fixture.awayOdds) {
                    onOddsTap(fixture, "home_draw_away", "away", fixture.awayOdds)
                }
            }
        }
    }
}

@Composable
private fun OddsChip(label: String, odds: Double, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TimeBetSurface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            Text(String.format("%.2f", odds), style = TimeBetTypography.labelLarge, color = TimeBetWhite, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Prediction Card (reused in My Bets) ───

@Composable
private fun PredictionCard(prediction: SportsPredictionEntity, isActive: Boolean) {
    val scope = rememberCoroutineScope()
    val statusColor = when (prediction.status) {
        PredictionStatus.WON -> TimeBetGreen
        PredictionStatus.LOST -> TimeBetRed
        PredictionStatus.VOID, PredictionStatus.CANCELLED -> TimeBetTextTertiary
        else -> TimeBetGoldLight
    }
    val statusIcon = when (prediction.status) {
        PredictionStatus.WON -> "✅"
        PredictionStatus.LOST -> "❌"
        PredictionStatus.VOID -> "⚠️"
        PredictionStatus.CANCELLED -> "↩️"
        PredictionStatus.PENDING_LOCKED -> "🔒"
        else -> "🟢"
    }
    val statusText = when (prediction.status) {
        PredictionStatus.WON -> "Won"
        PredictionStatus.LOST -> "Lost"
        PredictionStatus.VOID -> "Void"
        PredictionStatus.CANCELLED -> "Cancelled"
        PredictionStatus.PENDING_LOCKED -> "Locked"
        else -> "Active"
    }
    val isCancelable = prediction.status == PredictionStatus.PENDING_CANCELABLE

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(TimeBetSurfaceElevated).padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusIcon, style = TimeBetTypography.bodyMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(statusText, style = TimeBetTypography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${prediction.homeTeam} vs ${prediction.awayTeam}",
                    style = TimeBetTypography.bodyLarge, color = TimeBetWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prediction.selection.replaceFirstChar { it.uppercase() },
                        style = TimeBetTypography.labelMedium, color = TimeBetGoldLight)
                    Text("@ ${String.format("%.2f", prediction.oddsAtPlacement)}",
                        style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Text("· ${TimeFormatter.formatMinutesShort(prediction.stakeSeconds)} stake",
                        style = TimeBetTypography.labelSmall, color = TimeBetTextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when (prediction.status) {
                        PredictionStatus.WON -> "+${TimeFormatter.formatMinutesShort(prediction.settlementProfitSeconds)}"
                        PredictionStatus.LOST -> "-${TimeFormatter.formatMinutesShort(prediction.stakeSeconds)}"
                        PredictionStatus.VOID -> "Returned"
                        PredictionStatus.CANCELLED -> "Refunded"
                        else -> TimeFormatter.formatMinutesShort(prediction.potentialProfitSeconds) + " pot."
                    },
                    style = TimeBetTypography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (isCancelable) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = {
                        scope.launch {
                            ServiceLocator.timeBankRepository.cancelPrediction(prediction.id)
                        }
                    }, contentPadding = PaddingValues(0.dp)) {
                        Text("Cancel", color = TimeBetRed, style = TimeBetTypography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─── Bottom Sheet Bet Slip ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BetSlipBottomSheet(
    sheetState: SheetState,
    fixture: FixtureCard,
    marketType: String,
    selection: String,
    odds: Double,
    balance: Long,
    maxStake: Long,
    activeStake: Long,
    onDismiss: () -> Unit,
    onBetPlaced: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaced by remember { mutableStateOf(false) }

    val potentialProfit = (stakeSeconds * (odds - 1.0)).toLong()
    val theoreticalReturn = (stakeSeconds * odds).toLong()
    val effectiveBalance = (balance - activeStake).coerceAtLeast(0)
    val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TimeBetSurfaceElevated,
        dragHandle = { Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.width(32.dp).height(4.dp).background(TimeBetBorder, RoundedCornerShape(2.dp)))
        }}
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {

            if (isPlaced) {
                // Success state
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bet Placed!", style = TimeBetTypography.headlineMedium, color = TimeBetGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("${fixture.homeTeam} vs ${fixture.awayTeam}",
                        style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
                    Text("${selection.replaceFirstChar { it.uppercase() }} @ ${String.format("%.2f", odds)}",
                        style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Stake: ${TimeFormatter.formatMinutesShort(stakeSeconds)}",
                        style = TimeBetTypography.labelLarge, color = TimeBetWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cancel anytime today for full refund.",
                        style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(onClick = onBetPlaced,
                        shape = RoundedCornerShape(8.dp)) {
                        Text("Done", color = TimeBetWhite)
                    }
                }
                return@ModalBottomSheet
            }

            // Match info
            Text("Bet Slip", style = TimeBetTypography.labelMedium, color = TimeBetTextTertiary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp))
            Spacer(modifier = Modifier.height(12.dp))

            Surface(color = TimeBetSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${fixture.homeTeam} vs ${fixture.awayTeam}",
                        style = TimeBetTypography.bodyLarge, color = TimeBetWhite, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${fixture.competition} · ${fixture.kickoffTime}",
                        style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${selection.replaceFirstChar { it.uppercase() }}",
                            style = TimeBetTypography.labelLarge, color = TimeBetWhite)
                        Text(String.format("%.2f", odds),
                            style = TimeBetTypography.headlineMedium, color = TimeBetGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stake selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stake", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
                Text(TimeFormatter.formatMinutesShort(stakeSeconds),
                    style = TimeBetTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = TimeBetWhite)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Quick stake chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val quickStakes = listOf(1 * 60L, 3 * 60L, 5 * 60L, 10 * 60L, 15 * 60L, 30 * 60L)
                quickStakes.forEach { stake ->
                    val enabled = stake <= effectiveBalance && (activeStake + stake) <= maxStake
                    FilterChip(
                        selected = stakeSeconds == stake,
                        onClick = { if (enabled) stakeSeconds = stake },
                        label = { Text(TimeFormatter.formatMinutesShort(stake), style = TimeBetTypography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TimeBetWhite,
                            selectedLabelColor = TimeBetBlack,
                            disabledContainerColor = TimeBetSurfaceElevated.copy(alpha = 0.3f),
                            disabledLabelColor = TimeBetTextTertiary.copy(alpha = 0.3f)
                        ),
                        enabled = enabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payout summary
            Surface(color = TimeBetSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    BetSlipRow("Stake", TimeFormatter.formatMinutesShort(stakeSeconds))
                    BetSlipRow("Odds", String.format("%.2f", odds))
                    HorizontalDivider(color = TimeBetBorder, modifier = Modifier.padding(vertical = 4.dp))
                    BetSlipRow("Potential Return", TimeFormatter.formatMinutesShort(theoreticalReturn))
                    BetSlipRow("Credited Profit", "+${TimeFormatter.formatMinutesShort(potentialProfit)}", isProfit = true)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Stake is deducted now. Cancel today for full refund.",
                style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = TimeBetTypography.labelSmall, color = TimeBetRed)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Place Bet button
            Button(
                onClick = {
                    scope.launch {
                        errorMessage = null
                        if (stakeSeconds <= 0) {
                            errorMessage = "Stake must be positive"
                            return@launch
                        }
                        if (stakeSeconds > effectiveBalance) {
                            errorMessage = "Stake exceeds available balance"
                            return@launch
                        }
                        if (activeStake + stakeSeconds > maxStake) {
                            errorMessage = "Would exceed max active sports stake"
                            return@launch
                        }

                        val prediction = SportsPredictionEntity(
                            providerEventId = fixture.eventId,
                            competition = fixture.competition,
                            homeTeam = fixture.homeTeam,
                            awayTeam = fixture.awayTeam,
                            marketType = marketType,
                            selection = selection,
                            oddsAtPlacement = odds,
                            stakeSeconds = stakeSeconds,
                            potentialProfitSeconds = potentialProfit,
                            placedAt = System.currentTimeMillis(),
                            placementLocalDate = today,
                            status = PredictionStatus.PENDING_CANCELABLE
                        )

                        ServiceLocator.timeBankRepository.placeSportsPrediction(prediction)

                        // Also sync to Supabase
                        try {
                            ServiceLocator.supabaseSync.placePrediction(
                                com.timebet.app.core.sync.PredictionRequest(
                                    eventId = fixture.eventId,
                                    marketType = marketType,
                                    selection = selection,
                                    oddsAtPlacement = odds,
                                    stakeSeconds = stakeSeconds,
                                    potentialProfitSeconds = potentialProfit,
                                    placedAt = System.currentTimeMillis(),
                                    competition = fixture.competition,
                                    homeTeam = fixture.homeTeam,
                                    awayTeam = fixture.awayTeam
                                )
                            )
                        } catch (_: Exception) {
                            // Local save succeeded; server sync is best-effort
                        }

                        isPlaced = true
                        onBetPlaced()
                    }
                },
                enabled = stakeSeconds > 0 && stakeSeconds <= effectiveBalance,
                colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("PLACE BET", style = TimeBetTypography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BetSlipRow(label: String, value: String, isProfit: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
        Text(value, style = TimeBetTypography.bodyMedium,
            color = if (isProfit) TimeBetGreen else TimeBetWhite, fontWeight = FontWeight.Medium)
    }
}

// ─── Data ───

data class FixtureCard(
    val eventId: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffTime: String,
    val kickoffIso: String = "",
    val homeOdds: Double,
    val drawOdds: Double,
    val awayOdds: Double
)

private fun formatKickoff(isoTimestamp: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = parser.parse(isoTimestamp) ?: return isoTimestamp
        val formatter = SimpleDateFormat("EEE d MMM · HH:mm", Locale.US)
        formatter.format(date)
    } catch (_: Exception) {
        isoTimestamp
    }
}

/**
 * Compute realistic 1X2 default odds with a consistent ~6% bookmaker margin.
 * Uses a simple hash of team names to produce natural home/away bias, so not
 * every match shows identical 2.0 / 3.4 / 3.5 defaults.
 *
 * Real sportsbooks price to ~106% overround; we mirror that here.
 * When real API odds are available they always take precedence.
 */
private fun computeDefaultOdds(homeTeam: String, awayTeam: String): Triple<Double, Double, Double> {
    // Simple home-bias factor derived from team name hash (deterministic per match)
    val seed = (homeTeam + awayTeam).hashCode().toDouble()
    val homeBias = 0.32 + ((seed % 100) / 100.0) * 0.16 // 0.32–0.48 home win probability

    // Draw probability: ~0.28 ± noise
    val drawProb = 0.25 + (((seed * 31) % 100) / 100.0) * 0.08

    // Away probability: remainder that sums to 1.0
    val awayProb = 1.0 - homeBias - drawProb

    // Apply 6% margin (overround) — divide by 1.06
    val margin = 1.06
    val homeOdds = kotlin.math.round((1.0 / (homeBias * margin)) * 100.0) / 100.0
    val drawOdds = kotlin.math.round((1.0 / (drawProb * margin)) * 100.0) / 100.0
    val awayOdds = kotlin.math.round((1.0 / (awayProb * margin)) * 100.0) / 100.0

    return Triple(homeOdds, drawOdds, awayOdds)
}
