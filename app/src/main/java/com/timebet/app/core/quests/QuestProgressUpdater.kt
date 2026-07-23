package com.timebet.app.core.quests

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.QuestEntity
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Polls quest progress periodically. Step quests check Health Connect every 5 min.
 * Discipline quests check usage data every 1 min. Completes quests when goals are met.
 */
class QuestProgressUpdater(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        // Step quests — poll every 5 minutes
        scope.launch {
            while (isActive && isRunning) {
                try { updateStepQuests() } catch (_: Exception) {}
                delay(5 * 60 * 1000L)
            }
        }

        // Discipline quests — poll every 1 minute
        scope.launch {
            while (isActive && isRunning) {
                try { updateDisciplineQuests() } catch (_: Exception) {}
                delay(60 * 1000L)
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    private suspend fun updateStepQuests() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val active = ServiceLocator.database.questDao().getActive(today)
        val stepQuests = active.filter { it.type == "step" || it.type == "combo" }
        if (stepQuests.isEmpty()) return

        val steps = getTodaySteps()
        for (quest in stepQuests) {
            ServiceLocator.database.questDao().updateProgress(
                id = quest.id,
                currentValue = steps,
                status = if (steps >= quest.targetValue) "completed" else "active",
                completedAt = if (steps >= quest.targetValue) System.currentTimeMillis() else null
            )
            if (steps >= quest.targetValue && quest.type == "step") {
                creditReward(quest)
            }
        }
    }

    private suspend fun updateDisciplineQuests() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val active = ServiceLocator.database.questDao().getActive(today)
        val discQuests = active.filter { it.type == "discipline" }
        if (discQuests.isEmpty()) return

        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val breakdown = ServiceLocator.database.appUsageSessionDao()
            .getUsageBreakdown(startOfDay, now)
        val usageMap = breakdown.associate { it.packageName to it.totalSeconds }

        for (quest in discQuests) {
            val pkg = quest.targetPackageName ?: continue
            val currentUsage = usageMap[pkg] ?: 0L
            val isUnder = currentUsage < quest.targetValue
            ServiceLocator.database.questDao().updateProgress(
                id = quest.id,
                currentValue = currentUsage,
                status = "active", // discipline only completes at day end
                completedAt = null
            )
        }
    }

    private suspend fun getTodaySteps(): Long {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val now = Instant.now()
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            response.records.sumOf { it.count }
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun creditReward(quest: QuestEntity) {
        if (quest.status != "completed" || quest.claimedAt != null) return
        try {
            ServiceLocator.timeBankEngine.creditQuestReward(quest.rewardSeconds)
            ServiceLocator.database.questDao().claim(quest.id, System.currentTimeMillis())
        } catch (_: Exception) {}
    }
}
