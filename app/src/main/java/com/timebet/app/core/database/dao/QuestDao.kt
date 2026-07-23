package com.timebet.app.core.database.dao

import androidx.room.*
import com.timebet.app.core.database.entity.QuestEntity

@Dao
interface QuestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quest: QuestEntity)

    @Query("SELECT * FROM quests WHERE date = :date ORDER BY type ASC")
    suspend fun getByDate(date: String): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE date = :date AND status = 'active'")
    suspend fun getActive(date: String): List<QuestEntity>

    @Query("""
        UPDATE quests SET currentValue = :currentValue, status = :status, completedAt = :completedAt
        WHERE id = :id
    """)
    suspend fun updateProgress(id: String, currentValue: Long, status: String, completedAt: Long?)

    @Query("UPDATE quests SET status = 'claimed', claimedAt = :claimedAt WHERE id = :id")
    suspend fun claim(id: String, claimedAt: Long)

    @Query("UPDATE quests SET status = 'expired' WHERE status = 'active' AND date < :today")
    suspend fun expireOld(today: String)

    @Query("DELETE FROM quests WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
