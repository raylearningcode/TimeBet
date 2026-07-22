package com.timebet.app.features.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * History Screen — shows weekly, monthly, and all-time trends.
 * Users of screen-time apps expect to see their progress over time.
 */
@Composable
fun HistoryScreen() {
    var selectedPeriod by remember { mutableIntStateOf(0) }
    val periods = listOf("Week", "Month", "6 Months")

    // Data
    var dailyTotals by remember { mutableStateOf<List<com.timebet.app.core.database.dao.DailyTotal>>(emptyList()) }
    var weekTotal by remember { mutableLongStateOf(0L) }
    var monthTotal by remember { mutableLongStateOf(0L) }
    var yesterdayUsage by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

            dailyTotals = ServiceLocator.database.dailyUsageAggregateDao().getDailyTotals(180)

            // Calculate summaries
            val weekStart = today.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val monthStart = today.minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

            weekTotal = dailyTotals
                .filter { it.date >= weekStart && it.date < todayStr }
                .sumOf { it.totalSeconds }
            monthTotal = dailyTotals
                .filter { it.date >= monthStart && it.date < todayStr }
                .sumOf { it.totalSeconds }
            yesterdayUsage = dailyTotals
                .find { it.date == yesterday }?.totalSeconds ?: 0L

            // If no aggregates exist yet, compute from raw sessions
            if (dailyTotals.isEmpty()) {
                val now = System.currentTimeMillis()
                val weekStartMillis = today.minusDays(7).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                weekTotal = ServiceLocator.database.appUsageSessionDao().getTotalControlledUsage(weekStartMillis, now) ?: 0
                monthTotal = weekTotal // fallback
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TimeBetWhite)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period selector
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        periods.forEachIndexed { index, period ->
                            val selected = selectedPeriod == index
                            FilterChip(
                                selected = selected,
                                onClick = { selectedPeriod = index },
                                label = { Text(period, style = TimeBetTypography.labelSmall) },
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
                }

                // Summary cards
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "This Week",
                            value = TimeFormatter.formatHumanReadable(weekTotal),
                            icon = Icons.Filled.CalendarToday,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetGreen
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Yesterday",
                            value = TimeFormatter.formatHumanReadable(yesterdayUsage),
                            icon = Icons.Filled.Update,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Chart
                item {
                    val chartData = when (selectedPeriod) {
                        0 -> dailyTotals.take(7).reversed() // Week
                        1 -> dailyTotals.take(30) // Month
                        else -> dailyTotals.take(180) // 6 Months (aggregated by week)
                    }

                    if (chartData.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TimeBetSurfaceElevated)
                                .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    "Daily Usage",
                                    style = TimeBetTypography.labelLarge,
                                    color = TimeBetWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    when (selectedPeriod) {
                                        0 -> "Last 7 days"
                                        1 -> "Last 30 days"
                                        else -> "Last 6 months"
                                    },
                                    style = TimeBetTypography.labelSmall,
                                    color = TimeBetTextTertiary
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                val maxVal = chartData.maxOfOrNull { it.totalSeconds } ?: 1L
                                val displayData = when (selectedPeriod) {
                                    2 -> {
                                        // For 6-month view, aggregate by week
                                        chartData
                                            .groupBy { it.date.take(7) } // group by YYYY-MM (week approximation)
                                            .map { (week, list) ->
                                                com.timebet.app.core.database.dao.DailyTotal(
                                                    date = week,
                                                    totalSeconds = list.sumOf { it.totalSeconds }
                                                )
                                            }
                                            .takeLast(26) // ~6 months of weeks
                                    }
                                    else -> chartData
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    displayData.forEach { day ->
                                        val fraction = if (maxVal > 0) {
                                            (day.totalSeconds.toFloat() / maxVal).coerceIn(0.02f, 1f)
                                        } else 0.02f

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(if (selectedPeriod == 2) 6.dp else 12.dp)
                                                    .fillMaxHeight(fraction)
                                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                                    .background(
                                                        when {
                                                            day.totalSeconds == maxVal -> TimeBetGreen
                                                            day.totalSeconds > 0 -> TimeBetGreen.copy(alpha = 0.3f)
                                                            else -> TimeBetBorder
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Date labels for week view only
                                if (selectedPeriod == 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        displayData.takeLast(7).forEach { day ->
                                            val label = try {
                                                LocalDate.parse(day.date).dayOfWeek
                                                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                                    .take(3)
                                            } catch (_: Exception) { day.date.takeLast(2) }
                                            Text(
                                                label,
                                                style = TimeBetTypography.labelSmall,
                                                color = TimeBetTextTertiary,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // No history yet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TimeBetSurfaceElevated)
                                .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.TrendingUp, null, tint = TimeBetTextTertiary, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Not enough history yet",
                                    style = TimeBetTypography.bodyMedium,
                                    color = TimeBetTextSecondary
                                )
                                Text(
                                    "Data will appear after a few days of use",
                                    style = TimeBetTypography.labelSmall,
                                    color = TimeBetTextTertiary
                                )
                            }
                        }
                    }
                }

                // Per-app breakdown for selected period
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Summary",
                        style = TimeBetTypography.labelLarge,
                        color = TimeBetWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Total Used",
                            value = TimeFormatter.formatHumanReadable(
                                when (selectedPeriod) { 0 -> weekTotal; 1 -> monthTotal; else -> monthTotal * 6 }
                            ),
                            icon = Icons.Filled.Timer,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetAmber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Daily Avg",
                            value = TimeFormatter.formatMinutesShort(
                                when (selectedPeriod) {
                                    0 -> if (dailyTotals.take(7).isNotEmpty()) dailyTotals.take(7).sumOf { it.totalSeconds } / dailyTotals.take(7).size else 0
                                    1 -> if (dailyTotals.take(30).isNotEmpty()) dailyTotals.take(30).sumOf { it.totalSeconds } / dailyTotals.take(30).size else 0
                                    else -> if (dailyTotals.isNotEmpty()) dailyTotals.sumOf { it.totalSeconds } / dailyTotals.size else 0
                                }
                            ),
                            icon = Icons.Filled.Speed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier, accent: androidx.compose.ui.graphics.Color = TimeBetWhite
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = TimeBetTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = accent, maxLines = 1)
        }
    }
}
