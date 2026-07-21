package com.timebet.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timebet.app.ServiceLocator
import com.timebet.app.TimeBetApp
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.core.time.TimeBankEngine
import com.timebet.app.util.TimeBetConstants

/**
 * WorkManager worker for periodic sports prediction settlement.
 *
 * PRD Section 36: Settlement must be based on provider result data,
 * not client state. Client must never be trusted to declare a prediction won.
 *
 * Runs every 15 minutes to check for newly settled predictions from Supabase.
 * Updates local Room DB with results and credits profit for wins.
 */
class SportsSettlementWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = TimeBetApp.instance.database

    override suspend fun doWork(): Result {
        return try {
            val predictionDao = database.sportsPredictionDao()
            val userSettingsDao = database.userSettingsDao()

            // Ensure ServiceLocator is initialized
            try {
                ServiceLocator.timeBankEngine.ensureDailyReset()
            } catch (_: Exception) {
                // ServiceLocator might not be initialized yet in this worker context
            }

            // Check Supabase for newly settled predictions
            val settlements = try {
                ServiceLocator.supabaseSync.checkSettlements()
            } catch (e: Exception) {
                emptyList()
            }

            for (settlement in settlements) {
                try {
                    // Find the local prediction by provider event ID match
                    // Since checkSettlements returns predictions, we need to match by ID
                    val localId = settlement.predictionId.toLongOrNull() ?: continue
                    val local = predictionDao.getById(localId) ?: continue

                    // Only settle if still pending
                    if (local.status != PredictionStatus.PENDING_CANCELABLE &&
                        local.status != PredictionStatus.PENDING_LOCKED) {
                        continue
                    }

                    when (settlement.status) {
                        PredictionStatus.WON -> {
                            predictionDao.settle(
                                id = local.id,
                                newStatus = PredictionStatus.WON,
                                profit = settlement.profitSeconds
                            )
                            try {
                                ServiceLocator.timeBankEngine.creditSportsProfit(settlement.profitSeconds)
                            } catch (_: Exception) {}
                        }
                        PredictionStatus.LOST -> {
                            predictionDao.settle(
                                id = local.id,
                                newStatus = PredictionStatus.LOST,
                                profit = 0
                            )
                        }
                        PredictionStatus.VOID -> {
                            predictionDao.settle(
                                id = local.id,
                                newStatus = PredictionStatus.VOID,
                                profit = 0
                            )
                            try {
                                ServiceLocator.timeBankEngine.returnStake(local.stakeSeconds)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    // Skip failed settlements — will retry next cycle
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
