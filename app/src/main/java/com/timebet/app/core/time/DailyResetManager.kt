package com.timebet.app.core.time

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.timebet.app.core.database.dao.SportsPredictionDao
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.workers.DailyAggregationWorker
import com.timebet.app.workers.DailyResetWorker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Manages daily Time Bank reset.
 *
 * PRD Section 7.3: At local midnight, remaining balance expires,
 * new balance = base allowance, daily counters reset.
 *
 * Uses WorkManager for reliable periodic reset.
 */
class DailyResetManager(
    private val context: Context,
    private val timeBankEngine: TimeBankEngine,
    private val sportsPredictionDao: SportsPredictionDao,
    private val questGenerator: com.timebet.app.core.quests.QuestGenerator
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    companion object {
        private const val RESET_WORK_NAME = "timebet_daily_reset"
    }

    /**
     * Schedule the daily reset worker to run near midnight.
     * We schedule it for 00:05 am as a safety margin after midnight.
     * The worker also handles locking past-day sports predictions.
     */
    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(false) // Must run even if device is in use
            .build()

        val resetWork = PeriodicWorkRequestBuilder<DailyResetWorker>(
            repeatInterval = 24, TimeUnit.HOURS,
            flexTimeInterval = 15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RESET_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            resetWork
        )

        // Schedule daily aggregation (saves usage history, cleans old data)
        val aggregationWork = PeriodicWorkRequestBuilder<DailyAggregationWorker>(
            repeatInterval = 24, TimeUnit.HOURS,
            flexTimeInterval = 30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_aggregation",
            ExistingPeriodicWorkPolicy.KEEP,
            aggregationWork
        )
    }

    /**
     * Manually trigger the daily reset check.
     * Called on app startup and after reboot.
     *
     * PRD Section 32.5: After reboot, restore today's balance and reconcile sessions.
     */
    suspend fun checkAndResetIfNeeded() {
        val today = LocalDate.now().format(dateFormatter)
        val bank = timeBankEngine.getDailyBank()

        if (bank.date != today) {
            // New day — reset
            timeBankEngine.ensureDailyReset()
        }

        // Lock past sports predictions (PRD Section 17.3)
        sportsPredictionDao.lockPastPredictions(
            oldStatus = PredictionStatus.PENDING_CANCELABLE,
            newStatus = PredictionStatus.PENDING_LOCKED,
            today = today
        )

        // Generate today's quests if this is a new day
        if (bank.date != today) {
            try {
                val quests = questGenerator.generateDailyQuests(today)
                for (q in quests) {
                    com.timebet.app.ServiceLocator.database.questDao().upsert(q)
                }
            } catch (_: Exception) {}
        }

        // Settle discipline quests from yesterday (check if they stayed under target)
        try {
            val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
            val yesterdayQuests = com.timebet.app.ServiceLocator.database.questDao().getByDate(yesterday)
            val yesterdayUsage = getYesterdayUsageBreakdown()
            for (q in yesterdayQuests) {
                if (q.status == "active" && (q.type == "discipline" || q.type == "combo")) {
                    val pkg = q.targetPackageName
                    val usage = if (pkg != null) yesterdayUsage[pkg] ?: 0L else 0L
                    val completed = usage < q.targetValue
                    com.timebet.app.ServiceLocator.database.questDao().updateProgress(
                        id = q.id,
                        currentValue = usage,
                        status = if (completed) "completed" else "expired",
                        completedAt = if (completed) System.currentTimeMillis() else null
                    )
                    if (completed) {
                        timeBankEngine.creditQuestReward(q.rewardSeconds)
                        com.timebet.app.ServiceLocator.database.questDao().claim(q.id, System.currentTimeMillis())
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun getYesterdayUsageBreakdown(): Map<String, Long> {
        val yesterday = LocalDate.now().minusDays(1)
        val startOfDay = yesterday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val breakdown = com.timebet.app.ServiceLocator.database.appUsageSessionDao()
            .getUsageBreakdown(startOfDay, endOfDay)
        return breakdown.associate { it.packageName to it.totalSeconds }
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(RESET_WORK_NAME)
    }
}
