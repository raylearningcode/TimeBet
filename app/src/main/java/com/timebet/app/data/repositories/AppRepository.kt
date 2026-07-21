package com.timebet.app.data.repositories

import android.content.Context
import android.content.pm.PackageManager
import com.timebet.app.core.database.dao.AppUsageSessionDao
import com.timebet.app.core.database.dao.ControlledAppDao
import com.timebet.app.core.database.dao.DailyUsageAggregateDao
import com.timebet.app.core.database.entity.AppUsageSessionEntity
import com.timebet.app.core.database.entity.ControlledAppEntity
import com.timebet.app.core.database.entity.DailyUsageAggregateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class InstalledApp(
    val packageName: String,
    val appName: String
)

data class AppDetail(
    val packageName: String,
    val appName: String,
    val isControlled: Boolean,
    val todayUsageSeconds: Long,
    val weeklyAverageSeconds: Long,
    val sessionCount: Int,
    val longestSessionSeconds: Long,
    val percentageOfTotal: Double,
    val weeklyUsage: List<DailyUsagePoint>
)

data class DailyUsagePoint(
    val date: String,
    val usageSeconds: Long
)

class AppRepository(
    private val context: Context,
    private val controlledAppDao: ControlledAppDao,
    private val appUsageSessionDao: AppUsageSessionDao,
    private val dailyUsageAggregateDao: DailyUsageAggregateDao
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
        val sessionCount = sessions.size
        val longestSession = sessions.maxOfOrNull { it.durationSeconds } ?: 0
        val totalUsage = appUsageSessionDao.getTotalControlledUsage(startOfToday, endOfToday) ?: 0
        val percentage = if (totalUsage > 0) todayUsage.toDouble() / totalUsage else 0.0

        // Weekly usage
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

        return AppDetail(
            packageName = packageName,
            appName = app?.appName ?: packageName,
            isControlled = app?.isControlled ?: false,
            todayUsageSeconds = todayUsage,
            weeklyAverageSeconds = weeklyAvg,
            sessionCount = sessionCount,
            longestSessionSeconds = longestSession,
            percentageOfTotal = percentage,
            weeklyUsage = weeklyUsage
        )
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
