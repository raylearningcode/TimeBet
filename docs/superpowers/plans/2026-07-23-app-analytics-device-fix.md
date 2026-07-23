# App Detail Analytics Dashboard & Device Section Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild AppDetailScreen into a full analytics dashboard with hourly heatmaps, trends, session patterns, and rankings, while fixing hardcoded "Other Device" names in ActivityScreen and DevicesScreen by adding a `deviceName` column to the DB and sync.

**Architecture:** Data-first approach — add `deviceName` to `AppUsageSessionEntity`, add 4 new DAO queries for analytics, expand `AppDetail` model from 7 fields to 19, then rebuild the two screens on top of the enriched data. `SyncEngine.pullUsageSessions` will store real device names from Supabase.

**Tech Stack:** Kotlin, Jetpack Compose, Room DB, Material3, Coil for icons

## Global Constraints

- Room DB version bumped from 5 → 6 (existing `fallbackToDestructiveMigration()` pattern keeps migration simple)
- All time values in seconds; display via `TimeFormatter`
- Theme: `TimeBetBlack` background, `TimeBetSurfaceElevated` cards, `TimeBetBorder` borders
- Card shape: `RoundedCornerShape(14.dp)` for new cards
- Section spacing: 16dp between sections, 20dp horizontal padding
- No Supabase/backend changes needed (device_name already sent by push)

---

### Task 1: Add deviceName to AppUsageSessionEntity + bump DB version

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/database/entity/AppUsageSessionEntity.kt`
- Modify: `app/src/main/java/com/timebet/app/core/database/AppDatabase.kt`

**Interfaces:**
- Produces: `AppUsageSessionEntity.deviceName: String` (default `""`), available to all tasks consuming this entity

- [ ] **Step 1: Add deviceName field to the entity**

Open `app/src/main/java/com/timebet/app/core/database/entity/AppUsageSessionEntity.kt`. Add `val deviceName: String = ""` after the existing `deviceId` field:

```kotlin
@Entity(tableName = "app_usage_sessions")
data class AppUsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String = "",
    val startedAt: Long, // epoch millis
    val endedAt: Long? = null,
    val durationSeconds: Long = 0,
    val wasControlled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    // Sync columns
    val syncStatus: String = "pending", // "pending" | "synced"
    val serverId: String? = null,       // UUID from Supabase
    val deviceId: String = "unknown",
    val deviceName: String = ""         // NEW — e.g. "Samsung Galaxy S24"
)
```

- [ ] **Step 2: Bump DB version in AppDatabase**

Open `app/src/main/java/com/timebet/app/core/database/AppDatabase.kt`. Change `version = 5` to `version = 6`:

```kotlin
@Database(
    entities = [
        UserSettingsEntity::class,
        ControlledAppEntity::class,
        DailyTimeBankEntity::class,
        AppUsageSessionEntity::class,
        CasinoRoundEntity::class,
        SportsPredictionEntity::class,
        DailyUsageAggregateEntity::class
    ],
    version = 6,  // bumped from 5
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ... rest unchanged
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/database/entity/AppUsageSessionEntity.kt app/src/main/java/com/timebet/app/core/database/AppDatabase.kt
git commit -m "feat: add deviceName column to app_usage_sessions, bump DB to v6"
```

---

### Task 2: Add new DAO queries for analytics

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/database/dao/AppUsageSessionDao.kt`

**Interfaces:**
- Produces: `HourlyUsage(hour: Int, usageSeconds: Long)`, `AppSessionStats(count: Int, avgSeconds: Long, maxSeconds: Long, minSeconds: Long)`, `DeviceInfo(deviceId: String, deviceName: String)` data classes
- Produces: `getHourlyUsageForApp()`, `getAppSessionStats()`, `getDeviceAppBreakdown()`, `getDistinctDevices()` query functions
- Consumes: `AppUsageSessionEntity.deviceName` from Task 1

- [ ] **Step 1: Add data classes and queries to the DAO**

Open `app/src/main/java/com/timebet/app/core/database/dao/AppUsageSessionDao.kt`. Add the following data classes after the existing `SessionCountResult` class, and add the 4 new query methods inside the `@Dao` interface:

Add these data classes after `SessionCountResult` (around line 90):

```kotlin
data class HourlyUsage(
    val hour: Int,
    val usageSeconds: Long
)

data class AppSessionStats(
    val count: Int,
    val avgSeconds: Long,
    val maxSeconds: Long,
    val minSeconds: Long
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String
)
```

Add these queries inside the `AppUsageSessionDao` interface (before the closing `}`):

```kotlin
    /**
     * Hourly usage breakdown for a single app today.
     * Returns 24 slots (0-23), one per hour.
     */
    @Query("""
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch') AS INTEGER) as hour,
               SUM(durationSeconds) as usageSeconds
        FROM app_usage_sessions
        WHERE packageName = :packageName
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getHourlyUsageForApp(
        packageName: String, startOfDay: Long, endOfDay: Long
    ): List<HourlyUsage>

    /**
     * Session statistics for a single app today: count, avg, max, min duration.
     */
    @Query("""
        SELECT COUNT(*) as count,
               COALESCE(AVG(durationSeconds), 0) as avgSeconds,
               COALESCE(MAX(durationSeconds), 0) as maxSeconds,
               COALESCE(MIN(durationSeconds), 0) as minSeconds
        FROM app_usage_sessions
        WHERE packageName = :packageName
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getAppSessionStats(
        packageName: String, startOfDay: Long, endOfDay: Long
    ): AppSessionStats

    /**
     * Per-app usage breakdown for a specific device today.
     */
    @Query("""
        SELECT packageName, SUM(durationSeconds) as totalSeconds
        FROM app_usage_sessions
        WHERE deviceId = :deviceId
          AND wasControlled = 1
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY packageName
        ORDER BY totalSeconds DESC
    """)
    suspend fun getDeviceAppBreakdown(
        deviceId: String, startOfDay: Long, endOfDay: Long
    ): List<AppUsageBreakdown>

    /**
     * All distinct devices that have sessions today, with their names.
     */
    @Query("""
        SELECT DISTINCT deviceId, deviceName
        FROM app_usage_sessions
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
          AND deviceId != 'unknown'
    """)
    suspend fun getDistinctDevices(startOfDay: Long, endOfDay: Long): List<DeviceInfo>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/database/dao/AppUsageSessionDao.kt
git commit -m "feat: add hourly usage, session stats, device breakdown DAO queries"
```

---

### Task 3: Expand AppDetail model and add getDeviceAppUsage in AppRepository

**Files:**
- Modify: `app/src/main/java/com/timebet/app/data/repositories/AppRepository.kt`

**Interfaces:**
- Produces: `HourlySlot(hour: Int, usageSeconds: Long)`, expanded `AppDetail` (19 fields), `DeviceAppItem(packageName: String, appName: String, usageSeconds: Long)`
- Produces: `AppRepository.getDeviceAppUsage(deviceId: String): List<DeviceAppItem>`
- Consumes: `HourlyUsage` and `AppSessionStats` from Task 2 DAO

- [ ] **Step 1: Replace AppDetail, add HourlySlot and DeviceAppItem, update repository**

Open `app/src/main/java/com/timebet/app/data/repositories/AppRepository.kt`. Replace the existing `AppDetail` data class and its companion data classes, then replace the `getAppDetail` method, and add `getDeviceAppUsage`.

Replace the existing data classes (lines 21-36) with:

```kotlin
data class HourlySlot(
    val hour: Int,
    val usageSeconds: Long
)

data class AppDetail(
    val packageName: String,
    val appName: String,
    val isControlled: Boolean,
    // Today
    val todayUsageSeconds: Long,
    val hourlyUsage: List<HourlySlot>,      // 24 slots (0-23), filled even if zero
    val peakHour: Int,                       // 0-23, -1 if no usage
    // Sessions
    val sessionCount: Int,
    val avgSessionSeconds: Long,
    val longestSessionSeconds: Long,
    val shortestSessionSeconds: Long,
    // Trends (positive = increase, negative = decrease)
    val trendVsYesterday: Double,
    val trendVsLastWeek: Double,
    // Weekly
    val weeklyAverageSeconds: Long,
    val weeklyUsage: List<DailyUsagePoint>,
    val lastWeekUsage: List<DailyUsagePoint>,
    // Ranking
    val rankAmongControlled: Int,
    val totalControlledApps: Int,
    val percentageOfTotal: Double,
    val percentOfAllowance: Double
)

data class DailyUsagePoint(
    val date: String,
    val usageSeconds: Long
)

data class DeviceAppItem(
    val packageName: String,
    val appName: String,
    val usageSeconds: Long
)
```

Replace the `getAppDetail` method (lines 85-127) with the expanded version:

```kotlin
    suspend fun getAppDetail(packageName: String): AppDetail? {
        val today = LocalDate.now().format(dateFormatter)
        val startOfToday = LocalDate.now().atStartOfDay(
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()
        val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()

        val app = controlledAppDao.getByPackage(packageName)
        val sessions = appUsageSessionDao.getSessionsForApp(packageName, startOfToday, endOfToday)
        val todayUsage = sessions.sumOf { it.durationSeconds }
        val totalUsage = appUsageSessionDao.getTotalControlledUsage(startOfToday, endOfToday) ?: 0
        val percentage = if (totalUsage > 0) todayUsage.toDouble() / totalUsage else 0.0

        // Hourly breakdown — fill all 24 slots
        val hourlyRaw = appUsageSessionDao.getHourlyUsageForApp(packageName, startOfToday, endOfToday)
        val hourlyMap = hourlyRaw.associate { it.hour to it.usageSeconds }
        val hourlyUsage = (0..23).map { hour ->
            HourlySlot(hour = hour, usageSeconds = hourlyMap[hour] ?: 0L)
        }
        val peakHour = hourlyUsage.maxByOrNull { it.usageSeconds }?.let {
            if (it.usageSeconds > 0) it.hour else -1
        } ?: -1

        // Session stats
        val sessionStats = appUsageSessionDao.getAppSessionStats(packageName, startOfToday, endOfToday)

        // Weekly usage (this week, Mon-Sun)
        val weeklyUsage = mutableListOf<DailyUsagePoint>()
        for (i in 6 downTo 0) {
            val day = LocalDate.now().minusDays(i.toLong())
            val aggregates = dailyUsageAggregateDao.getByDateAndPackage(day.format(dateFormatter), packageName)
            weeklyUsage.add(
                DailyUsagePoint(
                    date = day.format(dateFormatter),
                    usageSeconds = aggregates?.usageSeconds ?: 0
                )
            )
        }
        val weeklyAvg = if (weeklyUsage.isNotEmpty()) weeklyUsage.map { it.usageSeconds }.average().toLong() else 0L

        // Last week usage (for comparison overlay)
        val lastWeekUsage = mutableListOf<DailyUsagePoint>()
        for (i in 13 downTo 7) {
            val day = LocalDate.now().minusDays(i.toLong())
            val aggregates = dailyUsageAggregateDao.getByDateAndPackage(day.format(dateFormatter), packageName)
            lastWeekUsage.add(
                DailyUsagePoint(
                    date = day.format(dateFormatter),
                    usageSeconds = aggregates?.usageSeconds ?: 0
                )
            )
        }

        // Yesterday usage for trend
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        val yesterdayAgg = dailyUsageAggregateDao.getByDateAndPackage(yesterday, packageName)
        val yesterdayUsage = yesterdayAgg?.usageSeconds ?: 0L
        val trendVsYesterday = if (yesterdayUsage > 0) {
            (todayUsage - yesterdayUsage).toDouble() / yesterdayUsage
        } else 0.0

        // Trend vs last week average
        val lastWeekTotal = lastWeekUsage.sumOf { it.usageSeconds }
        val lastWeekAvg = lastWeekTotal / 7.0
        val trendVsLastWeek = if (lastWeekAvg > 0) {
            (todayUsage - lastWeekAvg) / lastWeekAvg
        } else 0.0

        // Ranking among controlled apps
        val allControlled = controlledAppDao.getAll()
        val breakdown = appUsageSessionDao.getUsageBreakdown(startOfToday, endOfToday)
        val usageMap = breakdown.associate { it.packageName to it.totalSeconds }
        val ranked = allControlled
            .map { it.packageName to (usageMap[it.packageName] ?: 0L) }
            .sortedByDescending { it.second }
        val rank = ranked.indexOfFirst { it.first == packageName } + 1
        val totalControlled = allControlled.size

        // Percent of daily allowance
        val settings = ServiceLocator.database.userSettingsDao().get()
        val allowance = settings?.baseDailyAllowanceSeconds
            ?: com.timebet.app.util.TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS
        val percentOfAllowance = if (allowance > 0) todayUsage.toDouble() / allowance else 0.0

        return AppDetail(
            packageName = packageName,
            appName = app?.appName ?: packageName,
            isControlled = app?.isControlled ?: false,
            todayUsageSeconds = todayUsage,
            hourlyUsage = hourlyUsage,
            peakHour = peakHour,
            sessionCount = sessionStats.count,
            avgSessionSeconds = sessionStats.avgSeconds,
            longestSessionSeconds = sessionStats.maxSeconds,
            shortestSessionSeconds = sessionStats.minSeconds,
            trendVsYesterday = trendVsYesterday,
            trendVsLastWeek = trendVsLastWeek,
            weeklyAverageSeconds = weeklyAvg,
            weeklyUsage = weeklyUsage,
            lastWeekUsage = lastWeekUsage,
            rankAmongControlled = rank,
            totalControlledApps = totalControlled,
            percentageOfTotal = percentage,
            percentOfAllowance = percentOfAllowance
        )
    }
```

Note: `getAppDetail` now calls `ServiceLocator.database.userSettingsDao()` to get the daily allowance for `percentOfAllowance`. No constructor changes needed — `ServiceLocator` is already imported and used by the repository elsewhere.

- [ ] **Step 2: Add getDeviceAppUsage method**

Add this method to `AppRepository` (after `getAppDetail`):

```kotlin
    /**
     * Get per-app usage breakdown for a specific device today.
     */
    suspend fun getDeviceAppUsage(deviceId: String): List<DeviceAppItem> {
        val startOfToday = LocalDate.now().atStartOfDay(
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()
        val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()

        val breakdown = appUsageSessionDao.getDeviceAppBreakdown(deviceId, startOfToday, endOfToday)
        val allControlled = controlledAppDao.getAll()
        val nameMap = allControlled.associate { it.packageName to it.appName }

        return breakdown.map { entry ->
            DeviceAppItem(
                packageName = entry.packageName,
                appName = nameMap[entry.packageName] ?: entry.packageName,
                usageSeconds = entry.totalSeconds
            )
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/data/repositories/AppRepository.kt
git commit -m "feat: expand AppDetail model with hourly, trends, ranking; add getDeviceAppUsage"
```

---

### Task 4: Fix SyncEngine to store device_name on pull

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/sync/SyncEngine.kt`

**Interfaces:**
- Consumes: `AppUsageSessionEntity.deviceName` from Task 1 (entity now has the field)
- Produces: Pulled sessions now include real device names

- [ ] **Step 1: Add deviceName to pullUsageSessions insert**

Open `app/src/main/java/com/timebet/app/core/sync/SyncEngine.kt`. In the `pullUsageSessions` method, find the `database.appUsageSessionDao().insert(...)` call (around line 264-275) and add `deviceName`:

Replace:
```kotlin
                    database.appUsageSessionDao().insert(
                        AppUsageSessionEntity(
                            packageName = obj.getString("package_name"),
                            startedAt = obj.getLong("started_at"),
                            endedAt = obj.getLong("ended_at"),
                            durationSeconds = obj.getLong("duration_seconds"),
                            wasControlled = true,
                            syncStatus = "synced",
                            serverId = serverId,
                            deviceId = obj.optString("device_id", "unknown")
                        )
                    )
```

With:
```kotlin
                    database.appUsageSessionDao().insert(
                        AppUsageSessionEntity(
                            packageName = obj.getString("package_name"),
                            startedAt = obj.getLong("started_at"),
                            endedAt = obj.getLong("ended_at"),
                            durationSeconds = obj.getLong("duration_seconds"),
                            wasControlled = true,
                            syncStatus = "synced",
                            serverId = serverId,
                            deviceId = obj.optString("device_id", "unknown"),
                            deviceName = obj.optString("device_name", "")
                        )
                    )
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/sync/SyncEngine.kt
git commit -m "fix: store device_name when pulling usage sessions from Supabase"
```

---

### Task 5: Populate deviceName in ForegroundUsageMonitor when creating sessions

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/monitoring/ForegroundUsageMonitor.kt`

**Interfaces:**
- Consumes: `AppUsageSessionEntity.deviceName` from Task 1
- Consumes: `ServiceLocator.authManager.deviceName` for the device name value

- [ ] **Step 1: Add deviceName when inserting session in endCurrentSession**

Open `app/src/main/java/com/timebet/app/core/monitoring/ForegroundUsageMonitor.kt`. In the `endCurrentSession` method, find the `appUsageSessionDao.insert(...)` call (around line 372-380) and add `deviceName`:

Replace:
```kotlin
        scope.launch {
            appUsageSessionDao.insert(
                AppUsageSessionEntity(
                    packageName = session.packageName,
                    startedAt = session.startTime,
                    endedAt = endTime,
                    durationSeconds = durationSeconds,
                    wasControlled = true
                )
            )
        }
```

With:
```kotlin
        scope.launch {
            appUsageSessionDao.insert(
                AppUsageSessionEntity(
                    packageName = session.packageName,
                    startedAt = session.startTime,
                    endedAt = endTime,
                    durationSeconds = durationSeconds,
                    wasControlled = true,
                    deviceName = com.timebet.app.ServiceLocator.authManager.deviceName
                )
            )
        }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/monitoring/ForegroundUsageMonitor.kt
git commit -m "feat: populate deviceName when recording usage sessions"
```

---

### Task 6: Redesign AppDetailScreen into full analytics dashboard

**Files:**
- Modify: `app/src/main/java/com/timebet/app/features/home/AppDetailScreen.kt` (full rewrite)

**Interfaces:**
- Consumes: Expanded `AppDetail` model from Task 3 (19 fields including hourly, trends, ranking)
- Produces: 6-section analytics dashboard UI

- [ ] **Step 1: Write the complete redesigned AppDetailScreen**

Replace the entire contents of `app/src/main/java/com/timebet/app/features/home/AppDetailScreen.kt`:

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/timebet/app/features/home/AppDetailScreen.kt
git commit -m "feat: redesign AppDetailScreen into full analytics dashboard with 6 sections"
```

---

### Task 7: Fix device names in ActivityScreen + add ModalBottomSheet for other devices

**Files:**
- Modify: `app/src/main/java/com/timebet/app/features/activity/ActivityScreen.kt`

**Interfaces:**
- Consumes: `AppUsageSessionEntity.deviceName` from Task 1, `DeviceInfo` DAO result from Task 2, `AppRepository.getDeviceAppUsage()` from Task 3
- Produces: Real device names in "By Device" section, compact other-device rows, ModalBottomSheet popup

- [ ] **Step 1: Replace the "By Device" section in ActivityScreen**

Open `app/src/main/java/com/timebet/app/features/activity/ActivityScreen.kt`. Replace the existing device section (the block starting around line 166 `// Per-device usage data` through the device section ending around line 364) and the `DeviceUsageRow` composable (lines 1109-1162).

First, inside the `ScreenTimeTab` function, replace the per-device data loading block (lines 166-176):

Replace:
```kotlin
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
```

With:
```kotlin
    // Per-device usage data with real names
    var deviceUsageData by remember { mutableStateOf<List<DeviceUsageInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val now = System.currentTimeMillis()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val sessions = ServiceLocator.database.appUsageSessionDao().getByDateRange(startOfDay, now)
            val deviceMap = mutableMapOf<String, DeviceUsageInfo>()
            for (s in sessions) {
                val id = s.deviceId.ifEmpty { "unknown" }
                val name = s.deviceName.ifEmpty { id }
                val existing = deviceMap.getOrPut(id) {
                    DeviceUsageInfo(id, name, 0L)
                }
                deviceMap[id] = existing.copy(usageSeconds = existing.usageSeconds + s.durationSeconds)
            }
            deviceUsageData = deviceMap.values.toList()
        } catch (_: Exception) { }
    }
```

- [ ] **Step 2: Replace the "By Device" UI section**

Find the device section UI block (lines 349-364) — the `if (deviceUsageMap.size > 1)` block. Replace it with:

```kotlin
            // Per-device breakdown (shown when multiple devices synced)
            if (deviceUsageData.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("By Device")
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val currentDeviceId = ServiceLocator.authManager.deviceIdVal
                deviceUsageData.forEachIndexed { idx, device ->
                    val isCurrentDevice = device.deviceId == currentDeviceId
                    val fraction = if (todayUsage > 0) device.usageSeconds.toFloat() / todayUsage else 0f
                    item(key = "device_$idx") {
                        DeviceUsageRow(
                            deviceName = device.deviceName,
                            usageSeconds = device.usageSeconds,
                            fraction = fraction,
                            isCurrentDevice = isCurrentDevice,
                            onClick = if (!isCurrentDevice) {
                                { selectedDeviceForPopup = device }
                            } else null
                        )
                    }
                }
            }
```

- [ ] **Step 3: Add the popup state and ModalBottomSheet at the end of ScreenTimeTab**

Add these state variables after the existing `controlledApps` state (around line 192):

```kotlin
    // Device popup state
    var selectedDeviceForPopup by remember { mutableStateOf<DeviceUsageInfo?>(null) }
```

Add the ModalBottomSheet at the end of the `ScreenTimeTab` function (after the `LazyColumn` closing brace, before the function's closing brace):

```kotlin
    // Device App List Popup
    selectedDeviceForPopup?.let { device ->
        DeviceAppListPopup(
            device = device,
            onDismiss = { selectedDeviceForPopup = null }
        )
    }
```

- [ ] **Step 4: Replace DeviceUsageRow composable**

Replace the existing `DeviceUsageRow` (lines 1109-1162) with:

```kotlin
@Composable
private fun DeviceUsageRow(
    deviceName: String,
    usageSeconds: Long,
    fraction: Float,
    isCurrentDevice: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) Modifier.background(TimeBetSurfaceElevated, RoundedCornerShape(10.dp))
                    .border(0.5.dp, TimeBetBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick)
                else Modifier.background(TimeBetSurfaceElevated, RoundedCornerShape(10.dp))
                    .border(0.5.dp, if (isCurrentDevice) TimeBetGreen.copy(alpha = 0.3f) else TimeBetBorder, RoundedCornerShape(10.dp))
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PhoneAndroid,
            contentDescription = null,
            tint = if (isCurrentDevice) TimeBetGreen else TimeBetTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName, style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
                if (isCurrentDevice) {
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
        if (isCurrentDevice) {
            // Show fraction bar for current device
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
        } else {
            // Chevron for other devices
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View apps",
                tint = TimeBetTextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
```

- [ ] **Step 5: Add the DeviceAppListPopup composable and DeviceUsageInfo data class**

Add the imports at the top of the file if missing:
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import coil.compose.AsyncImage
import coil.request.ImageRequest
```

Add after `DeviceUsageRow` (before or at the end of the file):

```kotlin
private data class DeviceUsageInfo(
    val deviceId: String,
    val deviceName: String,
    val usageSeconds: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceAppListPopup(
    device: DeviceUsageInfo,
    onDismiss: () -> Unit
) {
    var appList by remember { mutableStateOf<List<DeviceAppItemRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(device.deviceId) {
        try {
            val items = ServiceLocator.appRepository.getDeviceAppUsage(device.deviceId)
            appList = items.map { item ->
                var icon by mutableStateOf<android.graphics.drawable.Drawable?>(null)
                try {
                    icon = context.packageManager.getApplicationIcon(item.packageName)
                } catch (_: Exception) {}
                DeviceAppItemRow(item.appName, item.packageName, item.usageSeconds, icon)
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        device.deviceName,
                        style = TimeBetTypography.headlineMedium,
                        color = TimeBetWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Apps used today",
                        style = TimeBetTypography.labelSmall,
                        color = TimeBetTextTertiary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = TimeBetTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TimeBetBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TimeBetWhite, modifier = Modifier.size(24.dp))
                }
            } else if (appList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            null,
                            tint = TimeBetTextTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No app usage recorded on this device today",
                            style = TimeBetTypography.bodyMedium,
                            color = TimeBetTextSecondary
                        )
                    }
                }
            } else {
                appList.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                            if (app.icon != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(app.icon).build(),
                                    contentDescription = app.appName,
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Text(
                                    app.appName.take(1).uppercase(),
                                    style = TimeBetTypography.labelLarge,
                                    color = TimeBetTextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            app.appName,
                            style = TimeBetTypography.bodyMedium,
                            color = TimeBetWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            TimeFormatter.formatHumanReadable(app.usageSeconds),
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetTextTertiary
                        )
                    }
                }
            }
        }
    }
}

private data class DeviceAppItemRow(
    val appName: String,
    val packageName: String,
    val usageSeconds: Long,
    val icon: android.graphics.drawable.Drawable?
)
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/timebet/app/features/activity/ActivityScreen.kt
git commit -m "feat: fix device names in Activity, add ModalBottomSheet for other device apps"
```

---

### Task 8: Fix hardcoded "Other Device" names in DevicesScreen

**Files:**
- Modify: `app/src/main/java/com/timebet/app/features/settings/DevicesScreen.kt`

**Interfaces:**
- Consumes: `AppUsageSessionEntity.deviceName` from Task 1
- Produces: Real device names in device list

- [ ] **Step 1: Replace hardcoded "Other Device" with real deviceName**

Open `app/src/main/java/com/timebet/app/features/settings/DevicesScreen.kt`. In the `collectDeviceUsage()` function (around lines 308-357), find the two places where `"Other Device"` is hardcoded.

Replace the first occurrence (line 326-327):
```kotlin
            val name = if (id == ServiceLocator.authManager.deviceIdVal) {
                ServiceLocator.authManager.deviceName
            } else {
                "Other Device"
            }
```

With:
```kotlin
            val name = if (id == ServiceLocator.authManager.deviceIdVal) {
                ServiceLocator.authManager.deviceName
            } else {
                s.deviceName.ifEmpty { "Device $id".take(12) }
            }
```

And the second occurrence (lines 344-347):
```kotlin
            val name = if (id == ServiceLocator.authManager.deviceIdVal) {
                ServiceLocator.authManager.deviceName
            } else {
                "Other Device"
            }
```

With:
```kotlin
            val name = if (id == ServiceLocator.authManager.deviceIdVal) {
                ServiceLocator.authManager.deviceName
            } else {
                s.deviceName.ifEmpty { "Device ${id.take(8)}" }
            }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/timebet/app/features/settings/DevicesScreen.kt
git commit -m "fix: use real device names instead of 'Other Device' in DevicesScreen"
```

---

### Task 9: Build verification

**Files:** (none — verification only)

- [ ] **Step 1: Verify the project compiles**

```bash
cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Fix any compilation errors before proceeding.

- [ ] **Step 2: Run existing tests**

```bash
cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew test
```

Expected: All tests pass (or same failures as before — no regressions).

- [ ] **Step 3: Final commit if any fixes were made**

```bash
git add -A
git commit -m "chore: build verification and fixes"
```

- [ ] **Step 4: Push**

```bash
git push origin master
```
