package com.timebet.app.features.home

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
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
import com.timebet.app.core.database.entity.ControlledAppEntity
import com.timebet.app.core.monitoring.ActiveAppState
import com.timebet.app.core.time.DailyTimeBankState
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home Screen — PRD Section 19.
 *
 * Immediately answers:
 * - How much time is left?
 * - How much has been used / won / lost?
 * - Which apps used the time?
 * - Is a controlled app currently active?
 */
@Composable
fun HomeScreen(
    onAppClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var bankState by remember { mutableStateOf<DailyTimeBankState?>(null) }
    val activeApp by ServiceLocator.usageMonitor.activeApp.collectAsState()
    val isMonitoring by ServiceLocator.usageMonitor.isMonitoring.collectAsState()
    var controlledApps by remember { mutableStateOf<List<ControlledAppEntity>>(emptyList()) }
    var appUsageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showMonitoringWarning by remember { mutableStateOf(false) }

    // Walk detection state
    val walkState by ServiceLocator.usageMonitor.let { monitor ->
        remember { mutableStateOf<com.timebet.app.core.monitoring.WalkState>(
            com.timebet.app.core.monitoring.WalkState.Stationary
        ) }
    }
    LaunchedEffect(Unit) {
        // Observe walk state from monitor's internal detector
        // Since WalkDetector is inside ForegroundUsageMonitor, we use a polling approach
        // For now: walk state observed indirectly via multiplier > 1.0
    }

    // Quest data
    var todayQuests by remember { mutableStateOf<List<com.timebet.app.core.database.entity.QuestEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            todayQuests = ServiceLocator.database.questDao().getByDate(today)
        } catch (_: Exception) {}
    }

    // Live timer state — ticks every second when an app is active
    var liveElapsed by remember { mutableLongStateOf(0L) }

    // Delay showing monitoring warning — service takes ~2s to start
    LaunchedEffect(Unit) {
        delay(3000L)
        if (!ServiceLocator.usageMonitor.isMonitoring.value) {
            showMonitoringWarning = true
        }
    }
    // Hide warning once monitoring starts working
    LaunchedEffect(isMonitoring) {
        if (isMonitoring) showMonitoringWarning = false
    }

    LaunchedEffect(refreshTrigger) {
        ServiceLocator.timeBankEngine.ensureDailyReset()

        launch {
            ServiceLocator.timeBankRepository.observeBalance().collectLatest { state ->
                bankState = state
            }
        }
        launch {
            ServiceLocator.appRepository.observeControlledApps().collectLatest { apps ->
                controlledApps = apps
            }
        }
    }

    // Live timer — ticks every second
    LaunchedEffect(activeApp) {
        if (activeApp is ActiveAppState.Active) {
            while (true) {
                val active = activeApp as ActiveAppState.Active
                liveElapsed = (System.currentTimeMillis() - active.sessionStartTime) / 1000
                delay(1000L)
            }
        } else {
            liveElapsed = 0L
        }
    }

    // Load real app usage
    LaunchedEffect(controlledApps, refreshTrigger) {
        try {
            val now = System.currentTimeMillis()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val breakdown = ServiceLocator.database.appUsageSessionDao()
                .getUsageBreakdown(startOfDay, now)
            appUsageMap = breakdown.associate { it.packageName to it.totalSeconds }
        } catch (_: Exception) {
            appUsageMap = emptyMap()
        }
    }

    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TimeBetBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TIMEBET",
                        style = TimeBetTypography.labelMedium,
                        color = TimeBetTextTertiary,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Text(
                        today,
                        style = TimeBetTypography.bodyMedium,
                        color = TimeBetTextSecondary
                    )
                }
                Row {
                    IconButton(onClick = {
                        refreshTrigger++
                        scope.launch {
                            ServiceLocator.timeBankEngine.ensureDailyReset()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = TimeBetTextSecondary
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = TimeBetTextSecondary
                        )
                    }
                }
            }

            // Monitoring status — only show when there's a confirmed problem (after 3s delay)
            if (showMonitoringWarning && !isMonitoring) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TimeBetRed.copy(alpha = 0.12f))
                        .border(0.5.dp, TimeBetRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, null, tint = TimeBetRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Monitoring paused — tap Fix",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetRed,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (ServiceLocator.permissionMonitor.hasUsageStatsPermission()) {
                            val intent = android.content.Intent(context, com.timebet.app.services.TimeBetForegroundService::class.java)
                            context.startForegroundService(intent)
                        } else {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }) {
                        Text("Fix", style = TimeBetTypography.labelSmall, color = TimeBetWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (!isMonitoring) 24.dp else 40.dp))

            // Main Balance Display
            val balance = bankState?.currentBalanceSeconds ?: 0
            Text(
                text = TimeFormatter.formatMinutesSeconds(balance),
                style = TimeBetTypography.displayLarge.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = TimeBetWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "REMAINING",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Base / Won / Lost summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SummaryChip("Base", TimeFormatter.formatMinutesShort(bankState?.baseAllowanceSeconds ?: 0))
                Spacer(modifier = Modifier.width(16.dp))
                SummaryChip("Won", "+${TimeFormatter.formatMinutesShort(bankState?.casinoProfitSeconds ?: 0)}", isPositive = true)
                Spacer(modifier = Modifier.width(16.dp))
                SummaryChip("Lost", "-${TimeFormatter.formatMinutesShort(bankState?.casinoLossSeconds ?: 0)}", isPositive = false)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Live controlled app indicator — with real-time ticking
            if (activeApp is ActiveAppState.Active) {
                val active = activeApp as ActiveAppState.Active
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimeBetSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NOW USING", style = TimeBetTypography.labelSmall, color = TimeBetGreen)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                controlledApps.find { it.packageName == active.packageName }?.appName
                                    ?: active.packageName,
                                style = TimeBetTypography.labelLarge,
                                color = TimeBetWhite
                            )
                        }
                        Text(
                            TimeFormatter.formatHoursMinutesSeconds(liveElapsed),
                            style = TimeBetTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                            color = TimeBetGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Used Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("USED TODAY", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                Text(
                    TimeFormatter.formatMinutesSeconds(bankState?.usedSeconds ?: 0),
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetWhite
                )
            }

            // Thin usage bar
            val baseAllowance = bankState?.baseAllowanceSeconds ?: 0
            val usageFraction = if (baseAllowance > 0) {
                ((bankState?.usedSeconds ?: 0).toFloat() / baseAllowance).coerceIn(0f, 1f)
            } else 0f

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TimeBetBorder, RoundedCornerShape(1.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usageFraction)
                        .height(2.dp)
                        .background(TimeBetWhite, RoundedCornerShape(1.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Walking Banner ── (only when walking)
            // We check if walk warning was triggered this session
            var walkActive by remember { mutableStateOf(false) }
            if (walkActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TimeBetAmber.copy(alpha = 0.12f))
                        .border(0.5.dp, TimeBetAmber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = TimeBetAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Walking detected — 2x time active",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Today's Quests ──
            if (todayQuests.isNotEmpty()) {
                Text(
                    "TODAY'S QUESTS",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
                Spacer(modifier = Modifier.height(10.dp))

                todayQuests.forEach { quest ->
                    QuestCard(quest = quest)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Entertainment Apps ──
            if (controlledApps.isNotEmpty()) {
                Text(
                    "ENTERTAINMENT APPS",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
                Spacer(modifier = Modifier.height(12.dp))

                controlledApps.forEach { app ->
                    val usage = appUsageMap[app.packageName] ?: 0L
                    AppUsageRow(
                        context = context,
                        appName = app.appName,
                        packageName = app.packageName,
                        usageSeconds = usage,
                        maxUsageSeconds = bankState?.usedSeconds ?: 1,
                        onClick = { onAppClick(app.packageName) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                // Empty state — no entertainment apps selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TimeBetSurfaceElevated)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = null,
                            tint = TimeBetTextTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "No entertainment apps selected",
                                style = TimeBetTypography.bodyMedium,
                                color = TimeBetTextSecondary
                            )
                            Text(
                                "Go to Settings to choose apps to track",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, isPositive: Boolean? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = TimeBetTypography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = when (isPositive) {
                true -> TimeBetGreen
                false -> TimeBetRed
                null -> TimeBetWhite
            },
            fontWeight = FontWeight.SemiBold
        )
        Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
    }
}

@Composable
private fun AppUsageRow(
    context: Context,
    appName: String,
    packageName: String,
    usageSeconds: Long,
    maxUsageSeconds: Long,
    onClick: () -> Unit
) {
    var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }

    // Load real app icon
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
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon — real or fallback
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TimeBetSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(appIcon).build(),
                    contentDescription = appName,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Fit
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

        Column(modifier = Modifier.weight(1f)) {
            Text(appName, style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
            Text(
                TimeFormatter.formatMinutesShort(usageSeconds),
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
        }

        // Mini usage bar
        val fraction = if (maxUsageSeconds > 0) {
            (usageSeconds.toFloat() / maxUsageSeconds).coerceIn(0f, 1f)
        } else 0f

        Box(
            modifier = Modifier
                .width(60.dp)
                .height(2.dp)
                .background(TimeBetBorder, RoundedCornerShape(1.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(TimeBetGreen.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun QuestCard(quest: com.timebet.app.core.database.entity.QuestEntity) {
    val icon = when (quest.type) {
        "step" -> Icons.Filled.DirectionsWalk
        "discipline" -> Icons.Filled.Timer
        else -> Icons.Filled.Stars
    }
    val accent = when (quest.status) {
        "completed" -> TimeBetGoldLight
        "claimed" -> TimeBetGreen
        "expired" -> TimeBetTextTertiary
        else -> TimeBetWhite
    }
    val fraction = if (quest.targetValue > 0) {
        (quest.currentValue.toFloat() / quest.targetValue).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, if (quest.status == "completed") TimeBetGoldLight.copy(alpha = 0.4f) else TimeBetBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(quest.title, style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
                Spacer(modifier = Modifier.height(6.dp))
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
                                when (quest.status) {
                                    "completed", "claimed" -> TimeBetGreen
                                    "expired" -> TimeBetBorder
                                    else -> TimeBetGreen.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    when (quest.type) {
                        "step" -> "${formatQuestValue(quest.currentValue)} / ${formatQuestValue(quest.targetValue)} steps"
                        "discipline" -> "${com.timebet.app.util.TimeFormatter.formatMinutesShort(quest.currentValue)} / ${com.timebet.app.util.TimeFormatter.formatMinutesShort(quest.targetValue)}"
                        else -> "${formatQuestValue(quest.currentValue)} / ${formatQuestValue(quest.targetValue)} steps"
                    },
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "+${quest.rewardSeconds / 60}m",
                    style = TimeBetTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = accent
                )
                Text(
                    when (quest.status) {
                        "completed" -> "Ready"
                        "claimed" -> "Earned"
                        "expired" -> "Missed"
                        else -> ""
                    },
                    style = TimeBetTypography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

private fun formatQuestValue(value: Long): String {
    return when {
        value >= 1000 -> "${value / 1000}.${(value % 1000) / 100}K"
        else -> "$value"
    }
}
