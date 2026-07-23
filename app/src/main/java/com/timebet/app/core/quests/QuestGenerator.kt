package com.timebet.app.core.quests

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.QuestEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Generates daily quests at reset time.
 *
 * Creates 3 quests: 1 step, 1 discipline, 1 combo.
 * Step targets based on 7-day average × 1.2. Discipline targets based on 7-day average usage × 0.75.
 */
class QuestGenerator(private val context: Context) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Generate quests for today. Called by DailyResetManager at midnight.
     */
    suspend fun generateDailyQuests(date: String): List<QuestEntity> {
        val avgSteps = getAverageSteps()
        val controlledApps = ServiceLocator.appRepository.getAllControlledApps()
        val usageBreakdown = getWeeklyUsageBreakdown()

        val quests = mutableListOf<QuestEntity>()

        // 1. Step quest
        val stepTarget = (avgSteps * 1.2).roundToLong().coerceAtLeast(3000)
        val stepReward = when {
            stepTarget >= 10000 -> 20L * 60
            stepTarget >= 7000 -> 15L * 60
            stepTarget >= 5000 -> 10L * 60
            else -> 5L * 60
        }
        quests.add(QuestEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            type = "step",
            title = "Walk ${formatSteps(stepTarget)} steps",
            targetValue = stepTarget,
            targetPackageName = null,
            currentValue = 0,
            rewardSeconds = stepReward,
            status = "active"
        ))

        // 2. Discipline quest — pick the most-used controlled app
        if (controlledApps.isNotEmpty()) {
            val topApp = usageBreakdown.maxByOrNull { it.value }
            if (topApp != null && topApp.value > 0) {
                val app = controlledApps.find { it.packageName == topApp.key }
                val targetUsage = (topApp.value * 0.75).roundToLong().coerceAtLeast(10L * 60)
                val disciplineReward = when {
                    targetUsage <= 20L * 60 -> 25L * 60
                    targetUsage <= 40L * 60 -> 20L * 60
                    targetUsage <= 60L * 60 -> 15L * 60
                    else -> 10L * 60
                }
                quests.add(QuestEntity(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    type = "discipline",
                    title = "${app?.appName ?: topApp.key} under ${formatSeconds(targetUsage)} today",
                    targetValue = targetUsage,
                    targetPackageName = topApp.key,
                    currentValue = 0,
                    rewardSeconds = disciplineReward,
                    status = "active"
                ))
            }
        }

        // 3. Combo quest — step + discipline
        if (quests.size >= 2) {
            val stepQ = quests[0]
            val discQ = quests[1]
            val comboReward = ((stepQ.rewardSeconds + discQ.rewardSeconds) * 0.7).roundToLong()
            quests.add(QuestEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                type = "combo",
                title = "${formatSteps(stepQ.targetValue)} steps + ${discQ.title.substringAfter(" ").substringBefore(" under")}",
                targetValue = stepQ.targetValue, // primary tracker is steps
                targetPackageName = discQ.targetPackageName,
                currentValue = 0,
                rewardSeconds = comboReward.coerceAtMost(30L * 60),
                status = "active"
            ))
        }

        // Cap total daily rewards at 45 min
        val totalRewards = quests.sumOf { it.rewardSeconds }
        if (totalRewards > 45L * 60) {
            val scaleFactor = (45.0 * 60.0) / totalRewards
            return quests.map { it.copy(rewardSeconds = (it.rewardSeconds * scaleFactor).roundToLong()) }
        }

        return quests
    }

    private suspend fun getAverageSteps(): Double {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val start = end.minusSeconds(7 * 24 * 3600)
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val totalSteps = response.records.sumOf { it.count }
            totalSteps / 7.0
        } catch (_: Exception) {
            5000.0 // default fallback: 5,000 steps
        }
    }

    private suspend fun getWeeklyUsageBreakdown(): Map<String, Long> {
        val now = System.currentTimeMillis()
        val startOfWeek = LocalDate.now().minusDays(7)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            val breakdown = ServiceLocator.database.appUsageSessionDao()
                .getUsageBreakdown(startOfWeek, now)
            return breakdown.associate { it.packageName to (it.totalSeconds / 7) } // daily average
        } catch (_: Exception) {
            return emptyMap()
        }
    }

    private fun formatSteps(steps: Long): String {
        return when {
            steps >= 1000 -> "${steps / 1000},${(steps % 1000) / 100}K"
            else -> "$steps"
        }
    }

    private fun formatSeconds(seconds: Long): String {
        val mins = seconds / 60
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
    }
}
