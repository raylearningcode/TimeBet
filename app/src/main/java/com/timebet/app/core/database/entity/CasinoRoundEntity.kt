package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "casino_rounds")
data class CasinoRoundEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String, // "coin_flip", "mines", "roulette", "blackjack", "crash", "chicken"
    val stakeSeconds: Long,
    val profitSeconds: Long = 0,
    val lossSeconds: Long = 0,
    val result: String, // "win", "loss", "push"
    val roundMetadataJson: String = "{}", // Game-specific data
    val startedAt: Long,
    val settledAt: Long = System.currentTimeMillis(),
    val status: String = "settled" // "initiated" | "settled" — PRD Section 43 crash recovery
)
