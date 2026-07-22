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
 * App Detail Screen — PRD Section 20.
 *
 * Shows: app icon, name, today's usage, 7-day average, session count,
 * longest session, percentage of total controlled usage, 7-day chart.
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
            Text("App Detail", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TimeBetWhite)
            }
        } else if (appDetail != null) {
            val detail = appDetail!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // App header — real icon with fallback
                val ctx = LocalContext.current
                var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                LaunchedEffect(detail.packageName) {
                    try {
                        appIcon = ctx.packageManager.getApplicationIcon(detail.packageName)
                    } catch (_: Exception) {}
                }

                // App info card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(TimeBetSurfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appIcon != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(appIcon).build(),
                                    contentDescription = detail.appName,
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
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
                                color = TimeBetWhite
                            )
                            Text(
                                detail.packageName,
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            if (detail.isControlled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Monitored",
                                    style = TimeBetTypography.labelSmall,
                                    color = TimeBetGreen
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stats grid
                Text(
                    "Today's Stats",
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = "Today",
                        value = TimeFormatter.formatHumanReadable(detail.todayUsageSeconds),
                        icon = Icons.Filled.Timer,
                        modifier = Modifier.weight(1f),
                        accent = TimeBetGreen
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        label = "7-Day Avg",
                        value = TimeFormatter.formatHumanReadable(detail.weeklyAverageSeconds),
                        icon = Icons.Filled.TrendingUp,
                        modifier = Modifier.weight(1f),
                        accent = TimeBetAmber
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = "Sessions",
                        value = "${detail.sessionCount}",
                        icon = Icons.Filled.Layers,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        label = "Longest",
                        value = TimeFormatter.formatHumanReadable(detail.longestSessionSeconds),
                        icon = Icons.Filled.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                StatCard(
                    label = "% of Total Usage",
                    value = "${(detail.percentageOfTotal * 100).toInt()}%",
                    icon = Icons.Filled.PieChart,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Weekly chart
                Text(
                    "Last 7 Days",
                    style = TimeBetTypography.labelLarge,
                    color = TimeBetWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                val maxUsage = detail.weeklyUsage.maxOfOrNull { it.usageSeconds } ?: 1L
                val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TimeBetSurfaceElevated)
                        .border(0.5.dp, TimeBetBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        // Total for week
                        val weekTotal = detail.weeklyUsage.sumOf { it.usageSeconds }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Week Total",
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            Text(
                                TimeFormatter.formatHumanReadable(weekTotal),
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Bar chart
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            detail.weeklyUsage.forEachIndexed { index, day ->
                                val heightFraction = if (maxUsage > 0) {
                                    (day.usageSeconds.toFloat() / maxUsage).coerceIn(0.04f, 1f)
                                } else 0f

                                // Get day of week from date string
                                val dayLabel = try {
                                    val date = LocalDate.parse(day.date, DateTimeFormatter.ISO_LOCAL_DATE)
                                    dayNames[date.dayOfWeek.value % 7]
                                } catch (_: Exception) {
                                    day.date.takeLast(2)
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Value above bar
                                    Text(
                                        TimeFormatter.formatMinutesShort(day.usageSeconds),
                                        style = TimeBetTypography.labelSmall,
                                        color = if (day.usageSeconds > 0) TimeBetTextSecondary else TimeBetTextTertiary,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Bar
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .fillMaxHeight(heightFraction)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(
                                                when {
                                                    day.usageSeconds == maxUsage -> TimeBetGreen
                                                    day.usageSeconds > 0 -> TimeBetGreen.copy(alpha = 0.4f)
                                                    else -> TimeBetBorder
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Day label
                                    Text(
                                        dayLabel,
                                        style = TimeBetTypography.labelSmall,
                                        color = TimeBetTextTertiary
                                    )
                                }
                            }
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
                Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
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
