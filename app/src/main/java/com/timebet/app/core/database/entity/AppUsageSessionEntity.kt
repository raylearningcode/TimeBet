package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage_sessions")
data class AppUsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String = "",
    val startedAt: Long, // epoch millis
    val endedAt: Long? = null,
    val durationSeconds: Long = 0,
    val wasControlled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
