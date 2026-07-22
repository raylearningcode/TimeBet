package com.timebet.app.core.database.dao

import androidx.room.*
import com.timebet.app.core.database.entity.AppUsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageSessionDao {

    @Insert
    suspend fun insert(session: AppUsageSessionEntity): Long

    @Update
    suspend fun update(session: AppUsageSessionEntity)

    @Query("SELECT * FROM app_usage_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AppUsageSessionEntity?

    @Query("SELECT * FROM app_usage_sessions WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): AppUsageSessionEntity?

    @Query("SELECT * FROM app_usage_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AppUsageSessionEntity>>

    @Query("""
        SELECT * FROM app_usage_sessions
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
        ORDER BY startedAt DESC
    """)
    suspend fun getByDateRange(startOfDay: Long, endOfDay: Long): List<AppUsageSessionEntity>

    @Query("""
        SELECT packageName, SUM(durationSeconds) as totalSeconds
        FROM app_usage_sessions
        WHERE wasControlled = 1
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY packageName
        ORDER BY totalSeconds DESC
    """)
    suspend fun getUsageBreakdown(startOfDay: Long, endOfDay: Long): List<AppUsageBreakdown>

    @Query("""
        SELECT SUM(durationSeconds) FROM app_usage_sessions
        WHERE wasControlled = 1
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getTotalControlledUsage(startOfDay: Long, endOfDay: Long): Long?

    @Query("SELECT * FROM app_usage_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getOpenSession(): AppUsageSessionEntity?

    @Query("""
        SELECT * FROM app_usage_sessions
        WHERE packageName = :packageName
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        ORDER BY startedAt DESC
    """)
    suspend fun getSessionsForApp(packageName: String, startOfDay: Long, endOfDay: Long): List<AppUsageSessionEntity>

    @Query("""
        SELECT packageName, COUNT(*) as count
        FROM app_usage_sessions
        WHERE wasControlled = 1
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY packageName
    """)
    suspend fun getSessionCounts(startOfDay: Long, endOfDay: Long): List<SessionCountResult>

    // ─── Sync methods ───

    @Query("SELECT * FROM app_usage_sessions WHERE syncStatus = 'pending' LIMIT 50")
    suspend fun getUnsynced(): List<AppUsageSessionEntity>

    @Query("UPDATE app_usage_sessions SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: Long)
}

data class AppUsageBreakdown(
    val packageName: String,
    val totalSeconds: Long
)

data class SessionCountResult(
    val packageName: String,
    val count: Int
)
