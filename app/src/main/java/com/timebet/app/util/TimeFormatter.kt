package com.timebet.app.util

import java.util.concurrent.TimeUnit

/**
 * Formats durations for display throughout the app.
 * All internal time values are in seconds.
 */
object TimeFormatter {

    /**
     * Formats seconds as MM:SS (e.g., "01:26" for 86 seconds).
     * Used for main balance display.
     */
    fun formatMinutesSeconds(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val minutes = TimeUnit.SECONDS.toMinutes(clamped)
        val seconds = clamped % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /**
     * Formats seconds as HH:MM:SS (e.g., "01:26:42").
     * Used for session timers.
     */
    fun formatHoursMinutesSeconds(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val hours = TimeUnit.SECONDS.toHours(clamped)
        val minutes = TimeUnit.SECONDS.toMinutes(clamped) % 60
        val seconds = clamped % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /**
     * Formats seconds as minutes with "m" suffix (e.g., "32m").
     * Used for app usage breakdown.
     */
    fun formatMinutesShort(totalSeconds: Long): String {
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds.coerceAtLeast(0))
        return "${minutes}m"
    }

    /**
     * Formats seconds as a human-readable duration (e.g., "1h 26m").
     */
    fun formatHumanReadable(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val hours = TimeUnit.SECONDS.toHours(clamped)
        val minutes = TimeUnit.SECONDS.toMinutes(clamped) % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /** Detailed format with seconds: "1h 26m 42s" or "3m 15s" */
    fun formatDetailed(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val hours = TimeUnit.SECONDS.toHours(clamped)
        val minutes = TimeUnit.SECONDS.toMinutes(clamped) % 60
        val seconds = clamped % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Formats an instant or timestamp to display time like "14:32".
     */
    fun formatTimeOfDay(hour: Int, minute: Int): String {
        return "%02d:%02d".format(hour, minute)
    }

    /**
     * Parses a duration string like "5m", "30m", "1h" to seconds.
     */
    fun parseDurationToSeconds(input: String): Long? {
        val trimmed = input.trim().lowercase()
        return when {
            trimmed.endsWith("h") -> {
                trimmed.removeSuffix("h").trim().toLongOrNull()?.let { it * 3600 }
            }
            trimmed.endsWith("m") -> {
                trimmed.removeSuffix("m").trim().toLongOrNull()?.let { it * 60 }
            }
            trimmed.endsWith("s") -> {
                trimmed.removeSuffix("s").trim().toLongOrNull()
            }
            else -> trimmed.toLongOrNull()
        }
    }
}

/**
 * Constants used throughout the app — all time values in seconds.
 */
object TimeBetConstants {
    /** Minimum base daily allowance: 30 minutes */
    const val MIN_BASE_ALLOWANCE_SECONDS = 30 * 60L

    /** Default base daily allowance: 120 minutes */
    const val DEFAULT_BASE_ALLOWANCE_SECONDS = 120 * 60L

    /** Maximum recommended base daily allowance: 6 hours */
    const val MAX_RECOMMENDED_ALLOWANCE_SECONDS = 6 * 3600L

    /** Maximum daily gambling profit: 75% of base allowance */
    const val MAX_DAILY_BONUS_PERCENTAGE = 0.75

    /** Maximum single bet: 50% of current balance to prevent burnout */
    const val MAX_STAKE_PERCENTAGE = 0.50

    /** Maximum active sports stake: 20% of base allowance */
    const val MAX_SPORTS_STAKE_PERCENTAGE = 0.20

    /** Low-time warning thresholds in seconds */
    val LOW_TIME_THRESHOLDS = listOf(10 * 60L, 5 * 60L, 60L)

    /** Quick stake presets in seconds */
    val QUICK_STAKES_SECONDS = listOf(5 * 60L, 10 * 60L, 15 * 60L, 30 * 60L)

    /** Quick stake percentage presets */
    val QUICK_STAKE_PERCENTAGES = listOf(0.25, 0.50)

    /** Quick base allowance options for onboarding */
    val BASE_ALLOWANCE_OPTIONS = listOf(
        1 * 3600L,   // 1 hour
        2 * 3600L,   // 2 hours
        3 * 3600L,   // 3 hours
        6 * 3600L    // 6 hours
    )
}
