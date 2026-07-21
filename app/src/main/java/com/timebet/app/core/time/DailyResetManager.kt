package com.timebet.app.core.time

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.timebet.app.core.database.dao.SportsPredictionDao
import com.timebet.app.core.database.entity.PredictionStatus
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
    private val sportsPredictionDao: SportsPredictionDao
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
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(RESET_WORK_NAME)
    }
}
