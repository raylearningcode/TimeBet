package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "casino_rounds")
data class CasinoRoundEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String,
    val stakeSeconds: Long,
    val profitSeconds: Long = 0,
    val lossSeconds: Long = 0,
    val result: String,
    val roundMetadataJson: String = "{}",
    val startedAt: Long,
    val settledAt: Long = System.currentTimeMillis(),
    val status: String = "settled",
    // Sync columns
    val syncStatus: String = "pending",
    val serverId: String? = null,
    val deviceId: String = "unknown"
)
