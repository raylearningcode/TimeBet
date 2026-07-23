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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * AI-optimized quest generator using data-driven difficulty scoring.
 *
 * Core principles:
 * - Difficulty = how many standard deviations above the user's normal this target is (z-score)
 * - Rewards scale with difficulty AND inversely with consistency (easy-for-you = less reward)
 * - No hardcoded thresholds — everything derived from user's actual 14-day behavior
 * - Users can create custom quests; rewards auto-calculated using the same formula
 */
class QuestGenerator(private val context: Context) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ── Configurable parameters (no hardcoded magic numbers) ──

    /** Baseline reward in seconds — scaled by difficulty and consistency */
    private val baseRewardSeconds = 15L * 60L  // 15 min

    /** Max daily quest reward cap */
    private val maxDailyQuestReward = 45L * 60L  // 45 min

    /** How many days of history to analyze */
    private val historyDays = 14

    /** Target z-score for step quests (how many std devs above mean) */
    private val stepStretchZScore = 0.7

    /** Minimum step target regardless of history */
    private val minStepTarget = 2000L

    /** Target reduction fraction for discipline quests (aim for X% of current usage) */
    private val disciplineReductionTarget = 0.70  // aim for 70% of current = 30% reduction

    /** How many quests to generate per day */
    private val dailyQuestCount = 3

    // ── Public API ──

    /**
     * Generate optimized daily quests.
     */
    suspend fun generateDailyQuests(date: String): List<QuestEntity> {
        val stepHistory = getStepHistory()
        val controlledApps = ServiceLocator.appRepository.getAllControlledApps()
        val usageBreakdown = getWeeklyUsageBreakdown()

        if (stepHistory.isEmpty() && controlledApps.isEmpty()) return emptyList()

        val quests = mutableListOf<QuestEntity>()

        // 1. Step quest — data-driven stretch goal
        if (stepHistory.isNotEmpty()) {
            val stats = computeStats(stepHistory)
            val target = computeStepTarget(stats)
            val difficulty = computeDifficulty(target, stats)
            val reward = computeReward(difficulty, stats.consistencyScore)

            quests.add(QuestEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                type = "step",
                title = buildStepTitle(target, difficulty),
                targetValue = target,
                targetPackageName = null,
                currentValue = 0,
                rewardSeconds = reward,
                status = "active"
            ))
        }

        // 2. Discipline quest — most-used app, data-driven reduction
        if (controlledApps.isNotEmpty() && usageBreakdown.isNotEmpty()) {
            val topApp = usageBreakdown.maxByOrNull { it.value }
            if (topApp != null && topApp.value > 60) { // at least 1 min/day avg
                val app = controlledApps.find { it.packageName == topApp.key }
                val appName = app?.appName ?: topApp.key
                val currentAvg = topApp.value // daily average seconds

                // Target: reduce by disciplineReductionTarget fraction
                val target = (currentAvg * disciplineReductionTarget).roundToLong()
                    .coerceAtLeast(5L * 60) // minimum 5 min

                // Difficulty: how much reduction relative to their normal variation
                val usageStats = computeUsageStats(topApp.key)
                val reduction = currentAvg - target
                val zScore = if (usageStats.stdDev > 0) reduction.toDouble() / usageStats.stdDev else 1.0
                val difficulty = classifyDifficulty(zScore)
                val reward = computeReward(difficulty, usageStats.consistencyScore)

                quests.add(QuestEntity(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    type = "discipline",
                    title = "$appName under ${formatSeconds(target)} today",
                    targetValue = target,
                    targetPackageName = topApp.key,
                    currentValue = 0,
                    rewardSeconds = reward,
                    status = "active"
                ))
            }
        }

        // 3. Combo quest — combines step + discipline stretch
        if (quests.size >= 2) {
            val stepQ = quests[0]
            val discQ = quests[1]
            // Combo reward = sum of individual rewards × 0.8 (discount for bundling)
            val comboReward = ((stepQ.rewardSeconds + discQ.rewardSeconds) * 0.8).roundToLong()
            quests.add(QuestEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                type = "combo",
                title = "${stepQ.title.replace("Walk ", "").replace(" steps", "")} steps + ${discQ.title.substringBefore(" under")} under limit",
                targetValue = stepQ.targetValue,
                targetPackageName = discQ.targetPackageName,
                currentValue = 0,
                rewardSeconds = comboReward,
                status = "active"
            ))
        }

        // Cap total daily rewards
        val totalRewards = quests.sumOf { it.rewardSeconds }
        if (totalRewards > maxDailyQuestReward) {
            val scale = maxDailyQuestReward.toDouble() / totalRewards
            return quests.map { it.copy(rewardSeconds = (it.rewardSeconds * scale).roundToLong()) }
        }

        return quests
    }

    /**
     * Calculate reward for a custom user-defined quest target.
     * Returns a QuestRewardPreview with difficulty label and estimated reward.
     */
    suspend fun previewCustomQuestReward(
        type: String,
        targetValue: Long,
        targetPackageName: String? = null
    ): QuestRewardPreview {
        return when (type) {
            "step" -> {
                val history = getStepHistory()
                if (history.isEmpty()) {
                    QuestRewardPreview("unknown", 10L * 60, "No step history yet — baseline 10 min reward")
                } else {
                    val stats = computeStats(history)
                    val difficulty = computeDifficulty(targetValue, stats)
                    val reward = computeReward(difficulty, stats.consistencyScore)
                    val avgFormatted = formatSteps(stats.mean.roundToLong())
                    val info = "Your 14-day avg: $avgFormatted steps (consistency: ${(stats.consistencyScore * 100).toInt()}%)"
                    QuestRewardPreview(difficulty.label, reward, info)
                }
            }
            "discipline" -> {
                val pkg = targetPackageName ?: return QuestRewardPreview("unknown", 5L * 60, "No app selected")
                val stats = computeUsageStats(pkg)
                if (stats.mean == 0L) {
                    QuestRewardPreview("easy", 15L * 60, "No usage history for this app — baseline 15 min")
                } else {
                    val reduction = stats.mean - targetValue
                    val zScore = if (stats.stdDev > 0) reduction.toDouble() / stats.stdDev else 1.0
                    val difficulty = classifyDifficulty(zScore.coerceAtLeast(0.0))
                    val reward = computeReward(difficulty, stats.consistencyScore)
                    val avgFormatted = formatSeconds(stats.mean)
                    val info = "Your daily avg: $avgFormatted (consistency: ${(stats.consistencyScore * 100).toInt()}%)"
                    QuestRewardPreview(difficulty.label, reward, info)
                }
            }
            else -> QuestRewardPreview("unknown", 5L * 60, "Unknown quest type")
        }
    }

    /**
     * Create a custom user-defined quest with auto-calculated reward.
     */
    suspend fun createCustomQuest(
        type: String,
        targetValue: Long,
        targetPackageName: String? = null
    ): QuestEntity {
        val today = LocalDate.now().format(dateFormatter)
        val preview = previewCustomQuestReward(type, targetValue, targetPackageName)

        val title = when (type) {
            "step" -> "Walk ${formatSteps(targetValue)} steps"
            "discipline" -> {
                val appName = targetPackageName?.let { pkg ->
                    ServiceLocator.appRepository.getAllControlledApps().find { it.packageName == pkg }?.appName
                } ?: targetPackageName ?: "App"
                "$appName under ${formatSeconds(targetValue)} today"
            }
            else -> "Custom quest"
        }

        return QuestEntity(
            id = UUID.randomUUID().toString(),
            date = today,
            type = type,
            title = title,
            targetValue = targetValue,
            targetPackageName = targetPackageName,
            currentValue = 0,
            rewardSeconds = preview.rewardSeconds,
            status = "active"
        )
    }

    // ── Statistics Engine ──

    private data class StepStats(
        val mean: Double,
        val stdDev: Double,
        val consistencyScore: Double,  // 0.0-1.0: how consistently user hits their mean
        val trend: Double,             // positive = increasing, negative = decreasing
        val values: List<Long>
    )

    private data class UsageStats(
        val mean: Long,
        val stdDev: Double,
        val consistencyScore: Double
    )

    private fun computeStats(dailySteps: List<Long>): StepStats {
        if (dailySteps.isEmpty()) return StepStats(0.0, 0.0, 0.0, 0.0, emptyList())

        val n = dailySteps.size
        val mean = dailySteps.sum().toDouble() / n
        val variance = dailySteps.map { (it - mean) * (it - mean) }.sum() / n
        val stdDev = sqrt(variance)

        // Consistency: what fraction of days are within 20% of the mean
        val consistentDays = dailySteps.count { abs(it - mean) / max(mean, 1.0) <= 0.2 }
        val consistencyScore = consistentDays.toDouble() / n

        // Trend: simple linear regression slope
        val xMean = (n - 1) / 2.0
        val yMean = mean
        val numerator = dailySteps.mapIndexed { i, y -> (i - xMean) * (y - yMean) }.sum()
        val denominator = (0 until n).map { (it - xMean) * (it - xMean) }.sum()
        val trend = if (denominator > 0) numerator / denominator else 0.0

        return StepStats(mean, stdDev, consistencyScore, trend, dailySteps)
    }

    private suspend fun computeUsageStats(packageName: String): UsageStats {
        val now = System.currentTimeMillis()
        val start = LocalDate.now().minusDays(historyDays.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            val sessions = ServiceLocator.database.appUsageSessionDao()
                .getSessionsForApp(packageName, start, now)
            val byDay = sessions.groupBy {
                Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            }
            val dailyTotals = byDay.map { (_, list) -> list.sumOf { s -> s.durationSeconds } }

            if (dailyTotals.isEmpty()) return UsageStats(0, 0.0, 0.0)

            val mean = dailyTotals.sum().toDouble() / dailyTotals.size
            val variance = dailyTotals.map { (it - mean) * (it - mean) }.sum() / dailyTotals.size
            val stdDev = sqrt(variance)
            val consistentDays = dailyTotals.count { abs(it - mean) / max(mean, 1.0) <= 0.25 }
            val consistency = consistentDays.toDouble() / dailyTotals.size

            return UsageStats(mean.roundToLong(), stdDev, consistency)
        } catch (_: Exception) {
            return UsageStats(0, 0.0, 0.0)
        }
    }

    // ── Target & Difficulty Calculation ──

    private fun computeStepTarget(stats: StepStats): Long {
        // Start with the stretch z-score, but adjust for consistency
        // Consistent users need a bigger push; inconsistent users get an easier target
        val adjustedZ = stepStretchZScore * (1.0 + stats.consistencyScore * 0.5)
        val target = (stats.mean + adjustedZ * max(stats.stdDev, 200.0)).roundToLong()

        // If user is trending up, push slightly harder
        val trendBonus = if (stats.trend > 0) (stats.trend * 3).roundToLong() else 0L

        return max(target + trendBonus, minStepTarget)
    }

    private fun computeDifficulty(target: Long, stats: StepStats): Difficulty {
        val zScore = (target - stats.mean) / max(stats.stdDev, 1.0)
        return classifyDifficulty(zScore)
    }

    private fun classifyDifficulty(zScore: Double): Difficulty {
        return when {
            zScore <= 0.2 -> Difficulty.MAINTENANCE
            zScore <= 0.6 -> Difficulty.EASY
            zScore <= 1.2 -> Difficulty.MEDIUM
            zScore <= 2.0 -> Difficulty.HARD
            else -> Difficulty.EXTREME
        }
    }

    enum class Difficulty(val label: String, val multiplier: Double) {
        MAINTENANCE("maintenance", 0.5),
        EASY("easy", 0.75),
        MEDIUM("medium", 1.0),
        HARD("hard", 1.4),
        EXTREME("extreme", 2.0)
    }

    /**
     * Reward formula: base × difficulty_multiplier × (1 - consistency × 0.4)
     *
     * A consistent 10K-step walker doing a 10K quest (maintenance) gets ~5 min.
     * An inconsistent 3K-step walker doing a 5K quest (hard) gets ~20 min.
     */
    private fun computeReward(difficulty: Difficulty, consistencyScore: Double): Long {
        val consistencyPenalty = 1.0 - consistencyScore * 0.4
        return (baseRewardSeconds * difficulty.multiplier * consistencyPenalty).roundToLong()
            .coerceIn(3L * 60, 30L * 60) // floor 3 min, ceiling 30 min per quest
    }

    // ── Data Sources ──

    private suspend fun getStepHistory(): List<Long> {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val start = end.minusSeconds(historyDays.toLong() * 24 * 3600)
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val byDay = response.records.groupBy {
                Instant.ofEpochMilli(it.startTime.toEpochMilli())
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString()
            }
            byDay.map { (_, records) -> records.sumOf { it.count } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getWeeklyUsageBreakdown(): Map<String, Long> {
        val now = System.currentTimeMillis()
        val startOfWeek = LocalDate.now().minusDays(7)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            val breakdown = ServiceLocator.database.appUsageSessionDao()
                .getUsageBreakdown(startOfWeek, now)
            return breakdown.associate { it.packageName to (it.totalSeconds / 7) }
        } catch (_: Exception) {
            return emptyMap()
        }
    }

    // ── Formatting ──

    private fun buildStepTitle(target: Long, difficulty: Difficulty): String {
        val steps = formatSteps(target)
        return when (difficulty) {
            Difficulty.MAINTENANCE -> "Maintain $steps steps"
            Difficulty.EASY -> "Walk $steps steps"
            Difficulty.MEDIUM -> "Walk $steps steps"
            Difficulty.HARD -> "Push for $steps steps"
            Difficulty.EXTREME -> "Challenge: $steps steps"
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

data class QuestRewardPreview(
    val difficulty: String,
    val rewardSeconds: Long,
    val info: String  // human-readable context about why this difficulty/reward
)
