package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton row
    val baseDailyAllowanceSeconds: Long = 120 * 60L, // Default 2 hours
    val resetTimezone: String = "UTC",
    val resetHour: Int = 0, // Midnight
    val sportsStakeLimitPercentage: Double = 0.20,
    val maxDailyBonusPercentage: Double = 0.75,
    val notificationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
