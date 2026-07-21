package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_time_bank",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyTimeBankEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // ISO date YYYY-MM-DD, unique per day
    val baseAllowanceSeconds: Long,
    val currentBalanceSeconds: Long,
    val casinoProfitSeconds: Long = 0,
    val casinoLossSeconds: Long = 0,
    val sportsProfitSeconds: Long = 0,
    val totalWinSeconds: Long = 0, // Total wins (not netted against losses) for daily bonus cap
    val usedSeconds: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
