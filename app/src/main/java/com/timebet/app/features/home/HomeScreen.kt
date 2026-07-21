package com.timebet.app.features.home

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
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
    var activeApp by remember { mutableStateOf<ActiveAppState>(ActiveAppState.None) }
    var controlledApps by remember { mutableStateOf<List<ControlledAppEntity>>(emptyList()) }
    var appUsageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Live timer state — ticks every second when an app is active
    var liveElapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        ServiceLocator.timeBankEngine.ensureDailyReset()

        launch {
            ServiceLocator.timeBankRepository.observeBalance().collectLatest { state ->
                bankState = state
            }
        }
        launch {
            ServiceLocator.usageMonitor.activeApp.collectLatest { state ->
                activeApp = state
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
    LaunchedEffect(controlledApps) {
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
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = TimeBetTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

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
            val usageFraction = if ((bankState?.baseAllowanceSeconds ?: 0) > 0) {
                ((bankState?.usedSeconds ?: 0).toFloat() / bankState!!.baseAllowanceSeconds).coerceIn(0f, 1f)
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

            Spacer(modifier = Modifier.height(32.dp))

            // App usage breakdown — with real icons and real usage data
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
