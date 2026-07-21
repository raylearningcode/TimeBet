package com.timebet.app.features.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.CasinoRoundEntity
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.core.database.entity.SportsPredictionEntity
import com.timebet.app.data.repositories.CasinoDayStats
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.ZoneId

/**
 * Activity Screen — PRD Section 27.
 * Three tabs: Screen Time, Casino, Sports.
 */
@Composable
fun ActivityScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Screen Time", "Casino", "Sports")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        Text(
            "ACTIVITY",
            style = TimeBetTypography.labelMedium,
            color = TimeBetTextTertiary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier.padding(start = 20.dp, top = 48.dp, bottom = 8.dp)
        )

        // Tab row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = TimeBetBlack,
            contentColor = TimeBetWhite,
            divider = { Box(modifier = Modifier.height(1.dp).background(TimeBetBorder)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    color = TimeBetWhite,
                    height = 2.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    modifier = Modifier.background(TimeBetBlack),
                    text = {
                        Text(
                            title,
                            color = if (isSelected) TimeBetWhite else TimeBetTextTertiary,
                            style = TimeBetTypography.labelLarge
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> ScreenTimeTab()
            1 -> CasinoTab()
            2 -> SportsTab()
        }
    }
}

@Composable
private fun ScreenTimeTab() {
    var todayUsage by remember { mutableLongStateOf(0L) }
    var baseAllowance by remember { mutableLongStateOf(0L) }
    var won by remember { mutableLongStateOf(0L) }
    var lost by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        ServiceLocator.timeBankRepository.observeBalance().collectLatest { state ->
            state?.let {
                todayUsage = it.usedSeconds
                baseAllowance = it.baseAllowanceSeconds
                won = it.netCasinoProfit.coerceAtLeast(0)
                lost = it.casinoLossSeconds
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Today's Screen Time", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                ActivityStatCard("Used", TimeFormatter.formatHumanReadable(todayUsage), Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                ActivityStatCard("Base", TimeFormatter.formatHumanReadable(baseAllowance), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ActivityStatCard("Won", "+${TimeFormatter.formatHumanReadable(won)}", Modifier.weight(1f), isPositive = true)
                Spacer(modifier = Modifier.width(12.dp))
                ActivityStatCard("Lost", "-${TimeFormatter.formatHumanReadable(lost)}", Modifier.weight(1f), isPositive = false)
            }
            Spacer(modifier = Modifier.height(12.dp))
            ActivityStatCard(
                "Unused",
                TimeFormatter.formatHumanReadable((baseAllowance - todayUsage).coerceAtLeast(0)),
                Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CasinoTab() {
    var stats by remember { mutableStateOf<CasinoDayStats?>(null) }
    var recentRounds by remember { mutableStateOf<List<CasinoRoundEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        stats = ServiceLocator.timeBankRepository.getDailyCasinoStats(startOfDay, endOfDay)

        ServiceLocator.timeBankRepository.observeRecentRounds().collectLatest { rounds ->
            recentRounds = rounds
        }
    }

    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        stats?.let { s ->
            item {
                Text("Today's Casino", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ActivityStatCard("Wagered", TimeFormatter.formatHumanReadable(s.totalWagered), Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    ActivityStatCard("Net", TimeFormatter.formatHumanReadable(s.netResult), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActivityStatCard("Win Rate", "${(s.winRate * 100).toInt()}%", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    ActivityStatCard("Rounds", "${s.totalCount}", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActivityStatCard("Best Win", "+${TimeFormatter.formatHumanReadable(s.largestWin)}", Modifier.weight(1f), isPositive = true)
                    Spacer(modifier = Modifier.width(12.dp))
                    ActivityStatCard("Worst Loss", "-${TimeFormatter.formatHumanReadable(s.largestLoss)}", Modifier.weight(1f), isPositive = false)
                }
                if (s.mostPlayedGame != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ActivityStatCard("Most Played", s.mostPlayedGame.replace("_", " ").replaceFirstChar { it.uppercase() }, Modifier.fillMaxWidth())
                }
            }
        }

        if (recentRounds.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Recent Rounds", style = TimeBetTypography.labelLarge, color = TimeBetWhite)
            }
            items(recentRounds.take(20)) { round ->
                RoundRow(round)
            }
        }
    }
}

@Composable
private fun SportsTab() {
    var predictions by remember { mutableStateOf<List<SportsPredictionEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        ServiceLocator.timeBankRepository.observeActivePredictions().collectLatest { preds ->
            predictions = preds
        }
    }

    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Sports Predictions", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            Spacer(modifier = Modifier.height(16.dp))

            val total = predictions.size
            val won = predictions.count { it.status == PredictionStatus.WON }
            val lost = predictions.count { it.status == PredictionStatus.LOST }
            val pending = predictions.count {
                it.status == PredictionStatus.PENDING_CANCELABLE || it.status == PredictionStatus.PENDING_LOCKED
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                ActivityStatCard("Total", "$total", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                ActivityStatCard("Pending", "$pending", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ActivityStatCard("Won", "$won", Modifier.weight(1f), isPositive = true)
                Spacer(modifier = Modifier.width(12.dp))
                ActivityStatCard("Lost", "$lost", Modifier.weight(1f), isPositive = false)
            }
        }

        if (predictions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(predictions) { prediction ->
                PredictionRow(prediction)
            }
        }
    }
}

@Composable
private fun ActivityStatCard(
    label: String, value: String, modifier: Modifier = Modifier,
    isPositive: Boolean? = null
) {
    Box(
        modifier = modifier
            .background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(value, style = TimeBetTypography.headlineMedium,
                color = when (isPositive) {
                    true -> TimeBetGreen; false -> TimeBetRed; null -> TimeBetWhite
                })
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        }
    }
}

@Composable
private fun RoundRow(round: CasinoRoundEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                round.gameType.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = TimeBetTypography.bodyMedium,
                color = TimeBetWhite
            )
            Text(
                TimeFormatter.formatMinutesShort(round.stakeSeconds),
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
        }
        Text(
            when (round.result) {
                "win" -> "+${TimeFormatter.formatMinutesShort(round.profitSeconds)}"
                "push" -> "±0"
                else -> "-${TimeFormatter.formatMinutesShort(round.lossSeconds)}"
            },
            style = TimeBetTypography.bodyMedium,
            color = when (round.result) {
                "win" -> TimeBetGreen
                "push" -> TimeBetAmber
                else -> TimeBetRed
            }
        )
    }
}

@Composable
private fun PredictionRow(prediction: SportsPredictionEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${prediction.homeTeam} vs ${prediction.awayTeam}",
                style = TimeBetTypography.bodyMedium,
                color = TimeBetWhite
            )
            Text(
                "${prediction.selection} @ ${String.format("%.2f", prediction.oddsAtPlacement)}",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                TimeFormatter.formatMinutesShort(prediction.stakeSeconds),
                style = TimeBetTypography.bodyMedium,
                color = TimeBetWhite
            )
            Text(
                when (prediction.status) {
                    PredictionStatus.WON -> "+${TimeFormatter.formatMinutesShort(prediction.settlementProfitSeconds)}"
                    PredictionStatus.PENDING_CANCELABLE -> "Pending"
                    PredictionStatus.PENDING_LOCKED -> "Locked"
                    PredictionStatus.LOST -> "Lost"
                    PredictionStatus.VOID -> "Void"
                    PredictionStatus.CANCELLED -> "Cancelled"
                    else -> prediction.status
                },
                style = TimeBetTypography.labelSmall,
                color = when (prediction.status) {
                    PredictionStatus.WON -> TimeBetGreen
                    PredictionStatus.LOST -> TimeBetRed
                    else -> TimeBetTextTertiary
                }
            )
        }
    }
}
