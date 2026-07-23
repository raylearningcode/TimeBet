package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey
    val id: String,                    // UUID
    val date: String,                  // "2026-07-23"
    val type: String,                  // "step" | "discipline" | "combo"
    val title: String,                 // "Walk 5,400 steps"
    val targetValue: Long,             // target steps or target usage seconds
    val targetPackageName: String?,    // null for step quests, package name for discipline
    val currentValue: Long = 0,        // current steps or current usage seconds
    val rewardSeconds: Long,           // time reward in seconds
    val status: String = "active",     // "active" | "completed" | "claimed" | "expired"
    val completedAt: Long? = null,     // epoch millis when completed
    val claimedAt: Long? = null        // epoch millis when reward claimed
)
