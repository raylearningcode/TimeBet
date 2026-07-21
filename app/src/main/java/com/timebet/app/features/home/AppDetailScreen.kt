package com.timebet.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.timebet.app.ServiceLocator
import com.timebet.app.data.repositories.AppDetail
import com.timebet.app.design.theme.*
import com.timebet.app.util.TimeFormatter

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
                // App header — real icon with fallback
                val ctx = LocalContext.current
                var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                LaunchedEffect(detail.packageName) {
                    try { appIcon = ctx.packageManager.getApplicationIcon(detail.packageName) } catch (_: Exception) {}
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TimeBetSurfaceElevated),
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(detail.appName, style = TimeBetTypography.headlineLarge, color = TimeBetWhite)
                Text(detail.packageName, style = TimeBetTypography.bodyMedium, color = TimeBetTextTertiary)

                Spacer(modifier = Modifier.height(32.dp))

                // Stats grid
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatBox("Today", TimeFormatter.formatMinutesShort(detail.todayUsageSeconds), Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    StatBox("7-Day Avg", TimeFormatter.formatMinutesShort(detail.weeklyAverageSeconds), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatBox("Sessions", "${detail.sessionCount}", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    StatBox("Longest", TimeFormatter.formatMinutesShort(detail.longestSessionSeconds), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                StatBox(
                    "% of Total",
                    "${(detail.percentageOfTotal * 100).toInt()}%",
                    Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Weekly chart (simple bar chart)
                Text("Last 7 Days", style = TimeBetTypography.labelLarge, color = TimeBetWhite)
                Spacer(modifier = Modifier.height(12.dp))

                val maxUsage = detail.weeklyUsage.maxOfOrNull { it.usageSeconds } ?: 1L
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    detail.weeklyUsage.forEach { day ->
                        val heightFraction = if (maxUsage > 0) {
                            (day.usageSeconds.toFloat() / maxUsage).coerceIn(0.02f, 1f)
                        } else 0f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                TimeFormatter.formatMinutesShort(day.usageSeconds),
                                style = TimeBetTypography.labelSmall,
                                color = TimeBetTextTertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .fillMaxHeight(heightFraction)
                                    .background(TimeBetWhite, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(value, style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = TimeBetTypography.labelSmall, color = TimeBetTextTertiary)
        }
    }
}
