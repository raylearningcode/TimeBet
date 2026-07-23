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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
            val walkActive by ServiceLocator.usageMonitor.isWalking.collectAsState()
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TODAY'S QUESTS",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                    // Refresh button
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val today = java.time.LocalDate.now()
                                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                    val existing = ServiceLocator.database.questDao().getByDate(today)
                                    if (existing.isEmpty()) {
                                        val quests = ServiceLocator.questGenerator.generateDailyQuests(today)
                                        for (q in quests) {
                                            ServiceLocator.database.questDao().upsert(q)
                                        }
                                    }
                                    todayQuests = ServiceLocator.database.questDao().getByDate(today)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            "Refresh quests",
                            tint = TimeBetTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                todayQuests.forEach { quest ->
                    QuestCard(quest = quest)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // No quests yet — show generate button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Stars,
                            null,
                            tint = TimeBetGoldLight,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No quests yet for today",
                            style = TimeBetTypography.bodyMedium,
                            color = TimeBetTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Generate daily quests to start earning bonus time",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextTertiary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val today = java.time.LocalDate.now()
                                            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                        val quests = ServiceLocator.questGenerator.generateDailyQuests(today)
                                        for (q in quests) {
                                            ServiceLocator.database.questDao().upsert(q)
                                        }
                                        todayQuests = ServiceLocator.database.questDao().getByDate(today)
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TimeBetGoldLight,
                                contentColor = TimeBetBlack
                            )
                        ) {
                            Text(
                                "Generate Quests",
                                style = TimeBetTypography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Quest Action Buttons ──
            var showCustomQuest by remember { mutableStateOf(false) }
            var isAiLoading by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { showCustomQuest = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, null, tint = TimeBetTextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Custom", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            isAiLoading = true
                            try {
                                // Get user stats for AI
                                val today = java.time.LocalDate.now()
                                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                val generator = ServiceLocator.questGenerator
                                // Use the existing daily quests or generate new ones via Gemini
                                val apps = ServiceLocator.appRepository.getAllControlledApps()
                                val now = System.currentTimeMillis()
                                val weekStart = java.time.LocalDate.now().minusDays(7)
                                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val breakdown = ServiceLocator.database.appUsageSessionDao()
                                    .getUsageBreakdown(weekStart, now)
                                val topApps = breakdown.take(3).map { b ->
                                    val app = apps.find { it.packageName == b.packageName }
                                    Pair(app?.appName ?: b.packageName, b.totalSeconds / 7 / 60)
                                }
                                val suggestions = com.timebet.app.core.quests.GeminiQuestAdvisor.getSuggestions(
                                    context, 5000.0, "stable", topApps
                                )
                                if (suggestions.isNotEmpty()) {
                                    for (s in suggestions) {
                                        val entity = com.timebet.app.core.database.entity.QuestEntity(
                                            id = java.util.UUID.randomUUID().toString(),
                                            date = today,
                                            type = s.type,
                                            title = s.title,
                                            targetValue = if (s.type == "step") s.targetSteps else s.targetMinutes * 60,
                                            targetPackageName = if (s.type == "discipline" || s.type == "combo")
                                                apps.find { it.appName.equals(s.targetApp, ignoreCase = true) }?.packageName
                                                ?: s.targetApp else null,
                                            currentValue = 0,
                                            rewardSeconds = s.rewardMinutes * 60,
                                            status = "active"
                                        )
                                        ServiceLocator.database.questDao().upsert(entity)
                                    }
                                    todayQuests = ServiceLocator.database.questDao().getByDate(today)
                                }
                            } catch (_: Exception) {
                                // Gemini unavailable — fall back silently, user can use Generate Quests
                            }
                            isAiLoading = false
                        }
                    },
                    enabled = !isAiLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(color = TimeBetGoldLight, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Stars, null, tint = TimeBetGoldLight, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Suggest", style = TimeBetTypography.labelSmall, color = TimeBetGoldLight)
                }
            }

            // Custom quest bottom sheet
            if (showCustomQuest) {
                CustomQuestSheet(
                    onDismiss = { showCustomQuest = false },
                    onQuestCreated = {
                        showCustomQuest = false
                        scope.launch {
                            try {
                                val today = java.time.LocalDate.now()
                                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                todayQuests = ServiceLocator.database.questDao().getByDate(today)
                            } catch (_: Exception) {}
                        }
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomQuestSheet(
    onDismiss: () -> Unit,
    onQuestCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("step") }
    var targetValue by remember { mutableLongStateOf(5000L) }
    var selectedApp by remember { mutableStateOf<com.timebet.app.core.database.entity.ControlledAppEntity?>(null) }
    var controlledApps by remember { mutableStateOf<List<com.timebet.app.core.database.entity.ControlledAppEntity>>(emptyList()) }
    var preview by remember { mutableStateOf<com.timebet.app.core.quests.QuestRewardPreview?>(null) }
    var isCalculating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            controlledApps = ServiceLocator.appRepository.getAllControlledApps()
        } catch (_: Exception) {}
    }

    // Recalculate preview when inputs change (debounced)
    LaunchedEffect(selectedType, targetValue, selectedApp) {
        isCalculating = true
        kotlinx.coroutines.delay(400) // debounce slider movement
        try {
            preview = ServiceLocator.questGenerator.previewCustomQuestReward(
                type = selectedType,
                targetValue = targetValue,
                targetPackageName = if (selectedType == "discipline") selectedApp?.packageName else null
            )
        } catch (_: Exception) {
            preview = null
        }
        isCalculating = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TimeBetSurfaceElevated,
        contentColor = TimeBetWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                "Create Custom Quest",
                style = TimeBetTypography.headlineMedium,
                color = TimeBetWhite,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Set your own target — reward is calculated from your data",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TimeBetBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Quest type selector
            Text("Quest Type", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("step" to "Steps", "discipline" to "App Limit").forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label, style = TimeBetTypography.labelSmall) },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Target value
            if (selectedType == "step") {
                // Dynamic minimum: 50% of user's average, floor at 1000
                val minSteps = maxOf(1000L, (preview?.info?.let {
                    Regex("avg: ([0-9,]+)").find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
                } ?: 2000L) / 2)
                if (targetValue < minSteps) targetValue = minSteps

                Text("Target Steps", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${formatQuestValue(targetValue)} steps",
                    style = TimeBetTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = TimeBetWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = targetValue.toFloat(),
                    onValueChange = { targetValue = maxOf(minSteps, it.toLong()) },
                    valueRange = minSteps.toFloat()..20000f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = TimeBetWhite,
                        activeTrackColor = TimeBetGreen,
                        inactiveTrackColor = TimeBetBorder
                    )
                )
                Text(
                    "Minimum: ${formatQuestValue(minSteps)} steps (50% of your average)",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1K", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Text("5K", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Text("10K", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Text("20K", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                }
            } else {
                // App picker for discipline
                Text("App to Limit", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                Spacer(modifier = Modifier.height(8.dp))
                if (controlledApps.isEmpty()) {
                    Text(
                        "No entertainment apps selected. Go to Settings to add some.",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                } else {
                    controlledApps.take(5).forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedApp?.packageName == app.packageName) TimeBetWhite.copy(alpha = 0.1f)
                                    else TimeBetSurfaceElevated
                                )
                                .clickable { selectedApp = app }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedApp?.packageName == app.packageName) Icons.Filled.RadioButtonChecked
                                else Icons.Filled.RadioButtonUnchecked,
                                null,
                                tint = if (selectedApp?.packageName == app.packageName) TimeBetGreen else TimeBetTextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(app.appName, style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
                        }
                    }
                }

                if (selectedApp != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Time Limit", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        com.timebet.app.util.TimeFormatter.formatHumanReadable(targetValue),
                        style = TimeBetTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        color = TimeBetWhite
                    )
                    Slider(
                        value = targetValue.toFloat(),
                        onValueChange = { targetValue = it.toLong() },
                        valueRange = (5 * 60).toFloat()..(3 * 3600).toFloat(),
                        steps = 34,
                        colors = SliderDefaults.colors(
                            thumbColor = TimeBetWhite,
                            activeTrackColor = TimeBetGreen,
                            inactiveTrackColor = TimeBetBorder
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5m", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Text("30m", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Text("1h", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                        Text("3h", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reward preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TimeBetSurfaceCard)
                    .padding(14.dp)
            ) {
                if (isCalculating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = TimeBetWhite, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculating reward...", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                    }
                } else if (preview != null) {
                    val p = preview!!
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Estimated Reward", style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
                            Text(
                                "+${p.rewardSeconds / 60}m",
                                style = TimeBetTypography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = TimeBetGoldLight
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Difficulty: ${p.difficulty.uppercase().replaceFirstChar { it.uppercase() }}",
                            style = TimeBetTypography.labelSmall,
                            color = when (p.difficulty) {
                                "maintenance", "easy" -> TimeBetGreen
                                "medium" -> TimeBetAmber
                                else -> TimeBetRed
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            p.info,
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextTertiary
                        )
                    }
                } else {
                    Text(
                        "Select a target to see reward preview",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Create button
            val canCreate = when (selectedType) {
                "step" -> true
                "discipline" -> selectedApp != null
                else -> false
            }
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val quest = ServiceLocator.questGenerator.createCustomQuest(
                                type = selectedType,
                                targetValue = targetValue,
                                targetPackageName = if (selectedType == "discipline") selectedApp?.packageName else null
                            )
                            ServiceLocator.database.questDao().upsert(quest)
                            onQuestCreated()
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = canCreate && preview != null && !isCalculating,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TimeBetGoldLight,
                    contentColor = TimeBetBlack
                )
            ) {
                Text("Create Quest", style = TimeBetTypography.labelLarge, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = TimeBetTextTertiary)
            }
        }
    }
}
