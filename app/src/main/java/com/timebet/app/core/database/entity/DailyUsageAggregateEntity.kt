package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Optional derived table for performance (PRD Section 33.7).
 * Updated after each session ends to avoid repeated aggregation queries.
 */
@Entity(
    tableName = "daily_usage_aggregates",
    indices = [Index(value = ["date", "packageName"], unique = true)]
)
data class DailyUsageAggregateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // ISO date YYYY-MM-DD
    val packageName: String,
    val appName: String = "",
    val usageSeconds: Long = 0,
    val sessionCount: Int = 0,
    val longestSessionSeconds: Long = 0
)
