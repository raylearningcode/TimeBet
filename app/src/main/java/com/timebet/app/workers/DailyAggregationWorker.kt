package com.timebet.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timebet.app.TimeBetApp
import com.timebet.app.core.database.entity.DailyUsageAggregateEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Runs daily to:
 * 1. Save per-app usage summaries to DailyUsageAggregateEntity
 * 2. Clean raw sessions older than 6 months (keep aggregates for trends)
 */
class DailyAggregationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailyAggregation"
        private const val RETENTION_MONTHS = 6L
    }

    override suspend fun doWork(): Result {
        return try {
            val database = TimeBetApp.instance.database
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val yesterday = LocalDate.now().minusDays(1)
            val yesterdayStr = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val startOfYesterday = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfYesterday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // 1. Aggregate yesterday's usage per app
            val breakdown = database.appUsageSessionDao().getUsageBreakdown(startOfYesterday, endOfYesterday)

            for (item in breakdown) {
                database.dailyUsageAggregateDao().upsert(
                    DailyUsageAggregateEntity(
                        date = yesterdayStr,
                        packageName = item.packageName,
                        usageSeconds = item.totalSeconds
                    )
                )
            }

            Log.d(TAG, "Aggregated ${breakdown.size} apps for $yesterdayStr")

            // 2. Clean raw sessions older than 6 months
            val cutoffLocalDate = LocalDate.now().minusMonths(RETENTION_MONTHS)
            val cutoffDateStr = cutoffLocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val cutoffMillis = cutoffLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            database.dailyUsageAggregateDao().deleteOlderThan(cutoffDateStr)
            database.appUsageSessionDao().deleteOlderThan(cutoffMillis)

            Log.d(TAG, "Cleaned records older than $cutoffDateStr")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Aggregation failed", e)
            Result.retry()
        }
    }
}
