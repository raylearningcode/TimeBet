package com.timebet.app.features.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.CasinoRoundEntity
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.core.database.entity.SportsPredictionEntity
import com.timebet.app.data.repositories.CasinoDayStats
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Activity Screen — PRD Section 27.
 * Three tabs: Screen Time, Casino, Sports.
 *
 * Shows daily usage breakdown, casino history, and sports predictions
 * with clear visual hierarchy and readable data presentation.
 */
@Composable
fun ActivityScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Screen Time", "Casino", "Sports", "History")
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Wrap in Surface to force dark background across all nested Material components
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TimeBetBlack,
        contentColor = TimeBetWhite
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Header with refresh
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ACTIVITY",
                style = TimeBetTypography.labelMedium,
                color = TimeBetTextTertiary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.padding(start = 20.dp, top = 48.dp, bottom = 8.dp)
            )
            IconButton(
                onClick = { refreshTrigger++ },
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = TimeBetTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Custom tab buttons — full control over colors, no Material3 Tab interference
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TimeBetBlack)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Column(
                        modifier = Modifier
                            .clickable(onClick = { selectedTab = index })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            title,
                            color = if (isSelected) TimeBetWhite else TimeBetTextTertiary,
                            style = TimeBetTypography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Selection indicator bar
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(2.dp)
                                .background(
                                    if (isSelected) TimeBetWhite else Color.Transparent,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
            // Divider below tabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TimeBetBorder)
            )
        }

        when (selectedTab) {
            0 -> ScreenTimeTab(refreshTrigger)
            1 -> CasinoTab(refreshTrigger)
            2 -> SportsTab(refreshTrigger)
            3 -> HistoryScreen()
        }
        } // Column
    } // Surface
}

// ─── Screen Time Tab ───

@Composable
private fun ScreenTimeTab(refreshKey: Int = 0) {
    var todayUsage by remember { mutableLongStateOf(0L) }
    var baseAllowance by remember { mutableLongStateOf(0L) }
    var currentBalance by remember { mutableLongStateOf(0L) }
    var won by remember { mutableLongStateOf(0L) }
    var lost by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var appUsageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var controlledApps by remember { mutableStateOf<List<com.timebet.app.core.database.entity.ControlledAppEntity>>(emptyList()) }

    LaunchedEffect(refreshKey) {
        ServiceLocator.timeBankRepository.observeBalance().collectLatest { state ->
            state?.let {
                todayUsage = it.usedSeconds
                baseAllowance = it.baseAllowanceSeconds
                currentBalance = it.currentBalanceSeconds
                won = it.casinoProfitSeconds
                lost = it.casinoLossSeconds
            }
            isLoading = false
        }
    }

    // Per-device usage data
    var deviceUsageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(Unit) {
        try {
            val now = System.currentTimeMillis()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val sessions = ServiceLocator.database.appUsageSessionDao().getByDateRange(startOfDay, now)
            deviceUsageMap = sessions
                .groupBy { it.deviceId.ifEmpty { "unknown" } }
                .mapValues { (_, list) -> list.sumOf { it.durationSeconds } }
        } catch (_: Exception) { }
    }

    // Load per-app usage breakdown
    LaunchedEffect(Unit) {
        try {
            val now = System.currentTimeMillis()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val breakdown = ServiceLocator.database.appUsageSessionDao()
                .getUsageBreakdown(startOfDay, now)
            appUsageMap = breakdown.associate { it.packageName to it.totalSeconds }
        } catch (_: Exception) {
            appUsageMap = emptyMap()
        }
        try {
            controlledApps = ServiceLocator.appRepository.getAllControlledApps()
        } catch (_: Exception) {
            controlledApps = emptyList()
        }
    }

    if (isLoading) {
        LoadingPlaceholder()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section title
            item {
                SectionHeader("Today's Screen Time")
            }

            // Balance overview — the most important numbers
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = "Remaining",
                        value = TimeFormatter.formatDetailed(currentBalance),
                        icon = Icons.Filled.Timer,
                        modifier = Modifier.weight(1f),
                        accent = TimeBetGreen
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        label = "Used",
                        value = TimeFormatter.formatDetailed(todayUsage),
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                        accent = TimeBetAmber
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = "Base Allowance",
                        value = TimeFormatter.formatHumanReadable(baseAllowance),
                        icon = Icons.Filled.Store,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        label = "Unused",
                        value = TimeFormatter.formatHumanReadable(
                            (baseAllowance - todayUsage + won - lost).coerceAtLeast(0)
                        ),
                        icon = Icons.Filled.Schedule,
                        modifier = Modifier.weight(1f),
                        accent = TimeBetTextSecondary
                    )
                }
            }

            // Casino impact on time
            if (won > 0 || lost > 0) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Won from Casino",
                            value = "+${TimeFormatter.formatHumanReadable(won)}",
                            icon = Icons.Filled.TrendingUp,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetGreen
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Lost to Casino",
                            value = "-${TimeFormatter.formatHumanReadable(lost)}",
                            icon = Icons.Filled.TrendingDown,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetRed
                        )
                    }
                }
            }

            // Usage bar
            item {
                val fraction = if (baseAllowance > 0) {
                    (todayUsage.toFloat() / baseAllowance).coerceIn(0f, 1f)
                } else 0f

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Daily Usage", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Text(
                            "${(fraction * 100).toInt()}%",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TimeBetBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        fraction > 0.75f -> TimeBetRed
                                        fraction > 0.50f -> TimeBetAmber
                                        else -> TimeBetGreen
                                    }
                                )
                        )
                    }
                }
            }

            // Per-app breakdown
            if (controlledApps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("App Breakdown")
                }

                controlledApps.forEach { app ->
                    val usage = appUsageMap[app.packageName] ?: 0L
                    val appFraction = if (todayUsage > 0) usage.toFloat() / todayUsage else 0f
                    item {
                        AppUsageListItem(
                            appName = app.appName,
                            packageName = app.packageName,
                            usageSeconds = usage,
                            fraction = appFraction
                        )
                    }
                }
            }

            // Empty state for no controlled apps
            if (controlledApps.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Apps,
                        title = "No Entertainment Apps Selected",
                        subtitle = "Go to Settings to choose which apps to track and limit."
                    )
                }
            }

            // Per-device breakdown (shown when multiple devices synced)
            if (deviceUsageMap.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("By Device")
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val currentDeviceId = ServiceLocator.authManager.deviceIdVal
                val currentDeviceName = ServiceLocator.authManager.deviceName
                deviceUsageMap.entries.forEachIndexed { idx, (deviceId, usage) ->
                    val name = if (deviceId == currentDeviceId) currentDeviceName else "Other Device"
                    val fraction = if (todayUsage > 0) usage.toFloat() / todayUsage else 0f
                    item(key = "device_$idx") {
                        DeviceUsageRow(name, usage, fraction, deviceId == currentDeviceId)
                    }
                }
            }
        }
    }
}

// ─── Casino Tab ───

@Composable
private fun CasinoTab(refreshKey: Int = 0) {
    var stats by remember { mutableStateOf<CasinoDayStats?>(null) }
    var recentRounds by remember { mutableStateOf<List<CasinoRoundEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        try {
            val now = System.currentTimeMillis()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            stats = ServiceLocator.timeBankRepository.getDailyCasinoStats(startOfDay, endOfDay)
        } catch (_: Exception) {
            stats = null
        }

        try {
            ServiceLocator.timeBankRepository.observeRecentRounds().collectLatest { rounds ->
                recentRounds = rounds
                isLoading = false
            }
        } catch (_: Exception) {
            recentRounds = emptyList()
            isLoading = false
        }
    }

    if (isLoading) {
        LoadingPlaceholder()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stats?.let { s ->
                item {
                    SectionHeader("Today's Casino")
                }

                // Net result — the headline number
                item {
                    val netFormatted = if (s.netResult >= 0) {
                        "+${TimeFormatter.formatHumanReadable(s.netResult)}"
                    } else {
                        "-${TimeFormatter.formatHumanReadable(-s.netResult)}"
                    }
                    StatCard(
                        label = "Net Result",
                        value = netFormatted,
                        icon = Icons.Filled.AccountBalance,
                        modifier = Modifier.fillMaxWidth(),
                        accent = if (s.netResult >= 0) TimeBetGreen else TimeBetRed
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Wagered",
                            value = TimeFormatter.formatHumanReadable(s.totalWagered),
                            icon = Icons.Filled.Casino,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Rounds",
                            value = "${s.totalCount}",
                            icon = Icons.Filled.Replay,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Win Rate",
                            value = "${(s.winRate * 100).toInt()}%",
                            icon = Icons.Filled.EmojiEvents,
                            modifier = Modifier.weight(1f),
                            accent = if (s.winRate >= 0.5f) TimeBetGreen else TimeBetAmber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = if (s.netResult >= 0) "Net Profit" else "Net Loss",
                            value = if (s.netResult >= 0) {
                                "+${TimeFormatter.formatHumanReadable(s.netResult)}"
                            } else {
                                "-${TimeFormatter.formatHumanReadable(-s.netResult)}"
                            },
                            icon = if (s.netResult >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                            modifier = Modifier.weight(1f),
                            accent = if (s.netResult >= 0) TimeBetGreen else TimeBetRed
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Best Win",
                            value = "+${TimeFormatter.formatHumanReadable(s.largestWin)}",
                            icon = Icons.Filled.Star,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetGreen
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Worst Loss",
                            value = "-${TimeFormatter.formatHumanReadable(s.largestLoss)}",
                            icon = Icons.Filled.Warning,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetRed
                        )
                    }
                }

                if (s.mostPlayedGame != null) {
                    item {
                        StatCard(
                            label = "Most Played",
                            value = s.mostPlayedGame.replace("_", " ")
                                .replaceFirstChar { it.uppercase() },
                            icon = Icons.Filled.Gamepad,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } ?: item {
                EmptyStateCard(
                    icon = Icons.Filled.Casino,
                    title = "No Casino Activity Today",
                    subtitle = "Play casino games to see your stats and history here."
                )
            }

            // Recent rounds
            if (recentRounds.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Recent Rounds")
                }

                itemsIndexed(recentRounds.take(30)) { index, round ->
                    RoundRow(round)
                    if (index < recentRounds.take(30).lastIndex) {
                        HorizontalDivider(
                            color = TimeBetBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Sports Tab ───

@Composable
private fun SportsTab(refreshKey: Int = 0) {
    var predictions by remember { mutableStateOf<List<SportsPredictionEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        try {
            ServiceLocator.timeBankRepository.observeActivePredictions().collectLatest { preds ->
                predictions = preds
                isLoading = false
            }
        } catch (_: Exception) {
            predictions = emptyList()
            isLoading = false
        }
    }

    if (isLoading) {
        LoadingPlaceholder()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader("Sports Predictions")
            }

            val total = predictions.size
            val won = predictions.count { it.status == PredictionStatus.WON }
            val lost = predictions.count { it.status == PredictionStatus.LOST }
            val pending = predictions.count {
                it.status == PredictionStatus.PENDING_CANCELABLE || it.status == PredictionStatus.PENDING_LOCKED
            }
            val void = predictions.count { it.status == PredictionStatus.VOID }
            val cancelled = predictions.count { it.status == PredictionStatus.CANCELLED }
            val totalStaked = predictions.sumOf { it.stakeSeconds }
            val totalProfit = predictions
                .filter { it.status == PredictionStatus.WON }
                .sumOf { it.settlementProfitSeconds }
            val totalLost = predictions
                .filter { it.status == PredictionStatus.LOST }
                .sumOf { it.stakeSeconds }

            if (predictions.isNotEmpty()) {
                // Summary cards
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Total Bets",
                            value = "$total",
                            icon = Icons.Filled.Receipt,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Pending",
                            value = "$pending",
                            icon = Icons.Filled.Pending,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetAmber
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Won",
                            value = "$won",
                            icon = Icons.Filled.CheckCircle,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetGreen
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "Lost",
                            value = "$lost",
                            icon = Icons.Filled.Cancel,
                            modifier = Modifier.weight(1f),
                            accent = TimeBetRed
                        )
                    }
                }

                if (void > 0 || cancelled > 0) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (void > 0) StatCard(
                                label = "Void",
                                value = "$void",
                                icon = Icons.Filled.RemoveCircle,
                                modifier = Modifier.weight(1f),
                                accent = TimeBetTextTertiary
                            )
                            if (void > 0 && cancelled > 0) Spacer(modifier = Modifier.width(12.dp))
                            if (cancelled > 0) StatCard(
                                label = "Cancelled",
                                value = "$cancelled",
                                icon = Icons.Filled.Undo,
                                modifier = Modifier.weight(1f),
                                accent = TimeBetTextTertiary
                            )
                            if (void == 0 && cancelled == 0) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Financial summary
                if (totalStaked > 0 || totalProfit > 0 || totalLost > 0) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatCard(
                                label = "Total Staked",
                                value = TimeFormatter.formatHumanReadable(totalStaked),
                                icon = Icons.Filled.ShoppingCart,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val netSports = totalProfit - totalLost
                            StatCard(
                                label = "Net P&L",
                                value = if (netSports >= 0) {
                                    "+${TimeFormatter.formatHumanReadable(netSports)}"
                                } else {
                                    "-${TimeFormatter.formatHumanReadable(-netSports)}"
                                },
                                icon = if (netSports >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                modifier = Modifier.weight(1f),
                                accent = if (netSports >= 0) TimeBetGreen else TimeBetRed
                            )
                        }
                    }
                }

                // Prediction list
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("All Predictions")
                }

                itemsIndexed(predictions) { index, prediction ->
                    PredictionRow(prediction)
                    if (index < predictions.lastIndex) {
                        HorizontalDivider(
                            color = TimeBetBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            } else {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.SportsSoccer,
                        title = "No Sports Predictions",
                        subtitle = "Place a prediction on upcoming matches in the Sports tab."
                    )
                }
            }
        }
    }
}

// ─── Shared Components ───

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = TimeBetTypography.headlineMedium,
        color = TimeBetWhite,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = TimeBetWhite
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label,
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                style = TimeBetTypography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppUsageListItem(
    appName: String,
    packageName: String,
    usageSeconds: Long,
    fraction: Float
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }

    LaunchedEffect(packageName) {
        try {
            appIcon = context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            appIcon = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, TimeBetBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TimeBetSurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(appIcon).build(),
                    contentDescription = appName,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Text(
                    appName.take(1).uppercase(),
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // App name + usage
        Column(modifier = Modifier.weight(1f)) {
            Text(
                appName,
                style = TimeBetTypography.bodyLarge,
                color = TimeBetWhite,
                maxLines = 1
            )
            Text(
                TimeFormatter.formatHumanReadable(usageSeconds),
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
        }

        // Usage bar
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${(fraction * 100).toInt()}%",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(TimeBetBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(TimeBetGreen.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
private fun RoundRow(round: CasinoRoundEntity) {
    val isWin = round.result == "win"
    val isPush = round.result == "push"
    val isLoss = round.result == "loss"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: game icon + details
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isWin -> TimeBetGreen
                            isPush -> TimeBetAmber
                            else -> TimeBetRed
                        }
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    round.gameType.replace("_", " ")
                        .replaceFirstChar { it.uppercase() },
                    style = TimeBetTypography.bodyMedium,
                    color = TimeBetWhite
                )
                Text(
                    buildString {
                        append("Stake: ${TimeFormatter.formatMinutesShort(round.stakeSeconds)}")
                        append(" · ")
                        append(
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(round.startedAt))
                        )
                    },
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }
        }

        // Right side: result
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when {
                    isWin -> "+${TimeFormatter.formatMinutesShort(round.profitSeconds)}"
                    isPush -> "±0"
                    else -> "-${TimeFormatter.formatMinutesShort(round.lossSeconds)}"
                },
                style = TimeBetTypography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = when {
                    isWin -> TimeBetGreen
                    isPush -> TimeBetAmber
                    else -> TimeBetRed
                }
            )
            Text(
                when {
                    isWin -> "Won"
                    isPush -> "Push"
                    else -> "Lost"
                },
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
        }
    }
}

@Composable
private fun PredictionRow(prediction: SportsPredictionEntity) {
    val isWon = prediction.status == PredictionStatus.WON
    val isLost = prediction.status == PredictionStatus.LOST
    val isPending = prediction.status == PredictionStatus.PENDING_CANCELABLE ||
                    prediction.status == PredictionStatus.PENDING_LOCKED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        // Match info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Match details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isWon -> TimeBetGreen
                                    isLost -> TimeBetRed
                                    isPending -> TimeBetAmber
                                    else -> TimeBetTextTertiary
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${prediction.homeTeam} vs ${prediction.awayTeam}",
                        style = TimeBetTypography.bodyMedium,
                        color = TimeBetWhite,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        prediction.selection.replaceFirstChar { it.uppercase() },
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextSecondary
                    )
                    Text(
                        " @ ${String.format("%.2f", prediction.oddsAtPlacement)}",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                    Text(
                        " · ${prediction.marketType.replace("_", " ")}",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatPredictionDate(prediction.placedAt),
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }

            // Stake + result
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    TimeFormatter.formatMinutesShort(prediction.stakeSeconds),
                    style = TimeBetTypography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = TimeBetWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
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
                    style = TimeBetTypography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = when {
                        isWon -> TimeBetGreen
                        isLost -> TimeBetRed
                        isPending -> TimeBetAmber
                        else -> TimeBetTextTertiary
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TimeBetTextTertiary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            title,
            style = TimeBetTypography.bodyLarge,
            color = TimeBetTextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            subtitle,
            style = TimeBetTypography.labelSmall,
            color = TimeBetTextTertiary,
            maxLines = 2
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TimeBetSurfaceElevated)
                    .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
            )
        }
    }
}

private fun formatPredictionDate(placedAt: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(placedAt)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val timeStr = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)

        when {
            date == today -> "Today at $timeStr"
            date == today.minusDays(1) -> "Yesterday at $timeStr"
            else -> "${DateTimeFormatter.ofPattern("MMM d").format(date)} at $timeStr"
        }
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun DeviceUsageRow(
    deviceName: String,
    usageSeconds: Long,
    fraction: Float,
    isThisDevice: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, if (isThisDevice) TimeBetGreen.copy(alpha = 0.3f) else TimeBetBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isThisDevice) Icons.Filled.PhoneAndroid else Icons.Filled.Tablet,
            contentDescription = null,
            tint = if (isThisDevice) TimeBetGreen else TimeBetTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName, style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
                if (isThisDevice) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("You", style = TimeBetTypography.labelSmall, color = TimeBetGreen)
                }
            }
            Text(
                TimeFormatter.formatHumanReadable(usageSeconds),
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
        }
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(TimeBetBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(TimeBetGreen.copy(alpha = 0.5f))
            )
        }
    }
}
