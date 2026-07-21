package com.timebet.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timebet.app.TimeBetApp
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.core.time.TimeBankEngine
import com.timebet.app.util.TimeBetConstants
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * WorkManager worker for daily Time Bank reset.
 *
 * PRD Section 7.3:
 * - Remaining usable balance expires
 * - New balance restored to base allowance
 * - Daily casino profit counter resets
 * - Daily usage totals begin a new day
 * - Lock past sports predictions
 */
class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = TimeBetApp.instance.database
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now().format(dateFormatter)
            val settingsDao = database.userSettingsDao()
            val settings = settingsDao.get()
            val baseAllowance = settings?.baseDailyAllowanceSeconds
                ?: TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS

            val timeBankDao = database.dailyTimeBankDao()
            val existing = timeBankDao.getByDate(today)

            if (existing == null) {
                // New day — create fresh bank
                timeBankDao.upsert(
                    com.timebet.app.core.database.entity.DailyTimeBankEntity(
                        date = today,
                        baseAllowanceSeconds = baseAllowance,
                        currentBalanceSeconds = baseAllowance
                    )
                )
            }

            // Lock past predictions
            database.sportsPredictionDao().lockPastPredictions(
                oldStatus = PredictionStatus.PENDING_CANCELABLE,
                newStatus = PredictionStatus.PENDING_LOCKED,
                today = today
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
