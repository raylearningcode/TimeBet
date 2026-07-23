package com.timebet.app.data.repositories

import android.content.Context
import android.content.pm.PackageManager
import com.timebet.app.core.database.dao.AppUsageSessionDao
import com.timebet.app.core.database.dao.ControlledAppDao
import com.timebet.app.core.database.dao.DailyUsageAggregateDao
import com.timebet.app.core.database.dao.UserSettingsDao
import com.timebet.app.core.database.entity.AppUsageSessionEntity
import com.timebet.app.core.database.entity.ControlledAppEntity
import com.timebet.app.core.database.entity.DailyUsageAggregateEntity
import com.timebet.app.util.TimeBetConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class InstalledApp(
    val packageName: String,
    val appName: String
)

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

class AppRepository(
    private val context: Context,
    private val controlledAppDao: ControlledAppDao,
    private val appUsageSessionDao: AppUsageSessionDao,
    private val dailyUsageAggregateDao: DailyUsageAggregateDao,
    private val userSettingsDao: UserSettingsDao
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Get all installed launchable apps (for the controlled apps picker).
     */
    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map {
                InstalledApp(
                    packageName = it.packageName,
                    appName = it.loadLabel(pm).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun observeControlledApps(): Flow<List<ControlledAppEntity>> = controlledAppDao.observeControlled()

    suspend fun getAllControlledApps(): List<ControlledAppEntity> = controlledAppDao.getAll()

    suspend fun setAppControlled(packageName: String, appName: String, controlled: Boolean) {
        if (controlled) {
            controlledAppDao.upsert(
                ControlledAppEntity(
                    packageName = packageName,
                    appName = appName,
                    isControlled = true
                )
            )
        } else {
            controlledAppDao.setControlled(packageName, false)
        }
    }

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

        // Weekly usage (rolling 7-day window ending today)
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
        } else if (todayUsage > 0) {
            1.0  // 100% increase — yesterday had 0, today has usage
        } else 0.0

        // Trend vs last week average
        val lastWeekTotal = lastWeekUsage.sumOf { it.usageSeconds }
        val lastWeekAvg = lastWeekTotal / 7.0
        val trendVsLastWeek = if (lastWeekAvg > 0) {
            (todayUsage - lastWeekAvg) / lastWeekAvg
        } else if (todayUsage > 0) {
            1.0
        } else 0.0

        // Ranking among controlled apps
        val allControlled = controlledAppDao.getAll()
        val breakdown = appUsageSessionDao.getUsageBreakdown(startOfToday, endOfToday)
        val usageMap = breakdown.associate { it.packageName to it.totalSeconds }
        val ranked = allControlled
            .map { it.packageName to (usageMap[it.packageName] ?: 0L) }
            .sortedByDescending { it.second }
        val totalControlled = allControlled.size
        val rank = ranked.indexOfFirst { it.first == packageName }.let { if (it >= 0) it + 1 else totalControlled }

        // Percent of daily allowance
        val settings = userSettingsDao.get()
        val allowance = settings?.baseDailyAllowanceSeconds
            ?: TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS
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

    /**
     * Persist a completed app usage session.
     */
    suspend fun recordSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        endedAt: Long,
        durationSeconds: Long
    ) {
        appUsageSessionDao.insert(
            AppUsageSessionEntity(
                packageName = packageName,
                appName = appName,
                startedAt = startedAt,
                endedAt = endedAt,
                durationSeconds = durationSeconds,
                wasControlled = true
            )
        )
    }

    fun observeRecentSessions(limit: Int = 50): Flow<List<AppUsageSessionEntity>> =
        appUsageSessionDao.observeRecent(limit)
}
