package com.timebet.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.timebet.app.ServiceLocator
import com.timebet.app.data.repositories.AppDetail
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * App Detail Screen — Full Analytics Dashboard.
 *
 * Six sections:
 * ① App Header — icon, name, package, monitored badge
 * ② Today's Snapshot — large timer, trend pills, mini stats
 * ③ Hourly Heatmap — 24-hour bar chart with peak indicator
 * ④ Weekly Trend — 7-day chart with last-week overlay comparison
 * ⑤ Ranking & Impact — rank among apps, % of allowance
 * ⑥ Session Patterns — avg sessions, type breakdown
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit
) {
    var appDetail by remember { mutableStateOf<AppDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(packageName) {
        appDetail = ServiceLocator.appRepository.getAppDetail(packageName)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TimeBetWhite
                )
            }
            Text("App Analytics", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TimeBetWhite)
            }
        } else if (appDetail != null) {
            val detail = appDetail!!
            val ctx = LocalContext.current
            var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
            LaunchedEffect(detail.packageName) {
                try {
                    appIcon = ctx.packageManager.getApplicationIcon(detail.packageName)
                } catch (_: Exception) {}
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // ─── ① App Header ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(TimeBetSurfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appIcon != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(appIcon).build(),
                                    contentDescription = detail.appName,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    detail.appName.take(2).uppercase(),
                                    style = TimeBetTypography.headlineMedium,
                                    color = TimeBetWhite
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                detail.appName,
                                style = TimeBetTypography.headlineMedium,
                                color = TimeBetWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                detail.packageName,
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            if (detail.isControlled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Visibility,
                                        contentDescription = null,
                                        tint = TimeBetGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Monitored", style = TimeBetTypography.labelSmall, color = TimeBetGreen)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── ② Today's Snapshot ───
                SectionHeader("Today's Snapshot", Icons.Filled.Timer, TimeBetGreen)
                Spacer(modifier = Modifier.height(12.dp))

                // Large timer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            TimeFormatter.formatDetailed(detail.todayUsageSeconds),
                            style = TimeBetTypography.displayLarge.copy(fontFeatureSettings = "tnum"),
                            color = TimeBetWhite
                        )
                        Text(
                            "used today",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trend pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrendPill(
                        label = "vs Yesterday",
                        trend = detail.trendVsYesterday,
                        modifier = Modifier.weight(1f)
                    )
                    TrendPill(
                        label = "vs Last Week",
                        trend = detail.trendVsLastWeek,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mini stat row
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStatCard(
                        label = "Sessions",
                        value = "${detail.sessionCount}",
                        icon = Icons.Filled.Layers,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    MiniStatCard(
                        label = "Avg Session",
                        value = TimeFormatter.formatMinutesShort(detail.avgSessionSeconds),
                        icon = Icons.Filled.Speed,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    MiniStatCard(
                        label = "Longest",
                        value = TimeFormatter.formatMinutesShort(detail.longestSessionSeconds),
                        icon = Icons.Filled.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── ③ Hourly Heatmap ───
                SectionHeader("Hourly Breakdown", Icons.Filled.AccessTime, TimeBetAmber)
                Spacer(modifier = Modifier.height(12.dp))

                val maxHourly = detail.hourlyUsage.maxOfOrNull { it.usageSeconds } ?: 1L
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            detail.hourlyUsage.forEach { slot ->
                                val fraction = if (maxHourly > 0) {
                                    (slot.usageSeconds.toFloat() / maxHourly).coerceIn(0.02f, 1f)
                                } else 0f
                                val isPeak = slot.hour == detail.peakHour

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .fillMaxHeight(fraction)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(
                                                when {
                                                    isPeak -> TimeBetGreen
                                                    slot.usageSeconds > 0 -> TimeBetGreen.copy(alpha = 0.35f)
                                                    else -> TimeBetBorder
                                                }
                                            )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Hour labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("12a", "6a", "12p", "6p", "11p").forEach { label ->
                                Text(
                                    label,
                                    style = TimeBetTypography.labelSmall,
                                    color = TimeBetTextTertiary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Peak indicator
                        if (detail.peakHour >= 0) {
                            val peakLabel = formatHourLabel(detail.peakHour)
                            Text(
                                "Most active: $peakLabel",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetGreen
                            )
                        } else {
                            Text(
                                "No usage recorded today",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── ④ Weekly Trend ───
                SectionHeader("Weekly Trend", Icons.Filled.TrendingUp, TimeBetGreen)
                Spacer(modifier = Modifier.height(12.dp))

                val maxWeekly = (detail.weeklyUsage.map { it.usageSeconds } +
                        detail.lastWeekUsage.map { it.usageSeconds })
                    .maxOfOrNull { it } ?: 1L
                val weekTotal = detail.weeklyUsage.sumOf { it.usageSeconds }
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        // Week total header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("This Week", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            Text(
                                TimeFormatter.formatHumanReadable(weekTotal),
                                style = TimeBetTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TimeBetWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Bar chart with overlay
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            detail.weeklyUsage.forEachIndexed { index, day ->
                                val dayLabel = try {
                                    val date = LocalDate.parse(day.date, DateTimeFormatter.ISO_LOCAL_DATE)
                                    dayNames[date.dayOfWeek.value - 1]
                                } catch (_: Exception) {
                                    day.date.takeLast(2)
                                }
                                val thisWeekH = if (maxWeekly > 0) {
                                    (day.usageSeconds.toFloat() / maxWeekly).coerceIn(0.03f, 1f)
                                } else 0f
                                val lastWeekVal = detail.lastWeekUsage.getOrNull(index)?.usageSeconds ?: 0L
                                val lastWeekH = if (maxWeekly > 0) {
                                    (lastWeekVal.toFloat() / maxWeekly).coerceIn(0.03f, 1f)
                                } else 0f

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        TimeFormatter.formatMinutesShort(day.usageSeconds),
                                        style = TimeBetTypography.labelSmall,
                                        color = if (day.usageSeconds > 0) TimeBetTextSecondary else TimeBetTextTertiary,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // This week bar + last week overlay dot
                                    Box(contentAlignment = Alignment.BottomCenter) {
                                        Box(
                                            modifier = Modifier
                                                .width(22.dp)
                                                .fillMaxHeight(thisWeekH)
                                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                                .background(
                                                    when {
                                                        day.usageSeconds == maxWeekly -> TimeBetGreen
                                                        day.usageSeconds > 0 -> TimeBetGreen.copy(alpha = 0.5f)
                                                        else -> TimeBetBorder
                                                    }
                                                )
                                        )
                                        // Last week dot overlay on top of bar
                                        if (lastWeekVal > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight(lastWeekH)
                                                    .width(22.dp),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(TimeBetAmber)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(dayLabel, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TimeBetGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("This week", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(TimeBetAmber)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Last week", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── ⑤ Ranking & Impact ───
                SectionHeader("Ranking & Impact", Icons.Filled.Leaderboard, TimeBetWhite)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        // Rank
                        Text(
                            "Ranked #${detail.rankAmongControlled} of ${detail.totalControlledApps} entertainment apps",
                            style = TimeBetTypography.bodyLarge,
                            color = TimeBetWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Allowance progress bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Daily Allowance",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            Text(
                                "${(detail.percentOfAllowance * 100).toInt()}%",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(TimeBetBorder)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(detail.percentOfAllowance.coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            detail.percentOfAllowance > 0.75f -> TimeBetRed
                                            detail.percentOfAllowance > 0.50f -> TimeBetAmber
                                            else -> TimeBetGreen
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // % of total controlled usage
                        Text(
                            "${(detail.percentageOfTotal * 100).toInt()}% of total entertainment time",
                            style = TimeBetTypography.bodyMedium,
                            color = TimeBetTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── ⑥ Session Patterns ───
                SectionHeader("Session Patterns", Icons.Filled.Analytics, TimeBetGreen)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        // Avg sessions per day (sessions / days with usage)
                        val daysWithUsage = detail.weeklyUsage.count { it.usageSeconds > 0 }.coerceAtLeast(1)
                        val avgSessionsPerDay = detail.sessionCount.toFloat() / daysWithUsage

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    String.format("%.1f", avgSessionsPerDay),
                                    style = TimeBetTypography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = TimeBetWhite
                                )
                                Text("sessions/day", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    TimeFormatter.formatMinutesShort(detail.avgSessionSeconds),
                                    style = TimeBetTypography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = TimeBetWhite
                                )
                                Text("avg length", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Session type breakdown (from sessions data)
                        val sessions = remember(detail.packageName) {
                            try {
                                val startOfToday = LocalDate.now().atStartOfDay(
                                    java.time.ZoneId.systemDefault()
                                ).toInstant().toEpochMilli()
                                val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(
                                    java.time.ZoneId.systemDefault()
                                ).toInstant().toEpochMilli()
                                ServiceLocator.database.appUsageSessionDao()
                                    .getSessionsForApp(detail.packageName, startOfToday, endOfToday)
                            } catch (_: Exception) { emptyList() }
                        }
                        val shortCount = sessions.count { it.durationSeconds in 1..299 }       // <5m
                        val mediumCount = sessions.count { it.durationSeconds in 300..1799 }    // 5-30m
                        val longCount = sessions.count { it.durationSeconds >= 1800 }            // 30m+
                        val totalCount = (shortCount + mediumCount + longCount).coerceAtLeast(1)

                        Text(
                            "Session Length Distribution",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Stacked bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TimeBetBorder)
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (shortCount > 0) Box(
                                    modifier = Modifier
                                        .weight(shortCount.toFloat())
                                        .fillMaxHeight()
                                        .background(TimeBetGreen.copy(alpha = 0.4f))
                                )
                                if (mediumCount > 0) Box(
                                    modifier = Modifier
                                        .weight(mediumCount.toFloat())
                                        .fillMaxHeight()
                                        .background(TimeBetGreen.copy(alpha = 0.7f))
                                )
                                if (longCount > 0) Box(
                                    modifier = Modifier
                                        .weight(longCount.toFloat())
                                        .fillMaxHeight()
                                        .background(TimeBetGreen)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text(
                                "${(shortCount * 100f / totalCount).toInt()}% Short (<5m)",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            Text(
                                "${(mediumCount * 100f / totalCount).toInt()}% Medium (5-30m)",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            Text(
                                "${(longCount * 100f / totalCount).toInt()}% Long (30m+)",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        } else {
            // Error / empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = TimeBetTextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Could not load app details",
                        style = TimeBetTypography.bodyLarge,
                        color = TimeBetTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Try selecting this app as a controlled app first",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                }
            }
        }
    }
}

// ─── Shared Components ───

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color = TimeBetWhite
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = TimeBetTypography.labelLarge,
            color = TimeBetWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TrendPill(
    label: String,
    trend: Double,
    modifier: Modifier = Modifier
) {
    val isUp = trend > 0
    val isDown = trend < 0
    val color = when {
        isUp -> TimeBetRed       // increase = more usage = bad
        isDown -> TimeBetGreen   // decrease = less usage = good
        else -> TimeBetTextTertiary
    }
    val arrow = when {
        isUp -> "↑"
        isDown -> "↓"
        else -> "→"
    }
    val pct = "${(kotlin.math.abs(trend) * 100).toInt()}%"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "$arrow $pct",
                style = TimeBetTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, TimeBetBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Icon(icon, null, tint = TimeBetTextSecondary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                style = TimeBetTypography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = TimeBetWhite,
                maxLines = 1
            )
            Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        }
    }
}

private fun formatHourLabel(hour: Int): String {
    return when (hour) {
        0 -> "12 AM"
        12 -> "12 PM"
        in 1..11 -> "$hour AM"
        in 13..23 -> "${hour - 12} PM"
        else -> ""
    }
}
