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

    @Query("DELETE FROM app_usage_sessions WHERE startedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    // ─── Analytics queries ───

    /**
     * Hourly usage breakdown for a single app today.
     * Returns 24 slots (0-23), one per hour.
     */
    @Query("""
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch') AS INTEGER) as hour,
               SUM(durationSeconds) as usageSeconds
        FROM app_usage_sessions
        WHERE packageName = :packageName
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getHourlyUsageForApp(
        packageName: String, startOfDay: Long, endOfDay: Long
    ): List<HourlyUsage>

    /**
     * Session statistics for a single app today: count, avg, max, min duration.
     */
    @Query("""
        SELECT COUNT(*) as count,
               COALESCE(AVG(durationSeconds), 0) as avgSeconds,
               COALESCE(MAX(durationSeconds), 0) as maxSeconds,
               COALESCE(MIN(durationSeconds), 0) as minSeconds
        FROM app_usage_sessions
        WHERE packageName = :packageName
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getAppSessionStats(
        packageName: String, startOfDay: Long, endOfDay: Long
    ): AppSessionStats

    /**
     * Per-app usage breakdown for a specific device today.
     */
    @Query("""
        SELECT packageName, SUM(durationSeconds) as totalSeconds
        FROM app_usage_sessions
        WHERE deviceId = :deviceId
          AND wasControlled = 1
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY packageName
        ORDER BY totalSeconds DESC
    """)
    suspend fun getDeviceAppBreakdown(
        deviceId: String, startOfDay: Long, endOfDay: Long
    ): List<AppUsageBreakdown>

    /**
     * All distinct devices that have sessions today, with their names.
     */
    @Query("""
        SELECT DISTINCT deviceId, deviceName
        FROM app_usage_sessions
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
          AND deviceId != 'unknown'
    """)
    suspend fun getDistinctDevices(startOfDay: Long, endOfDay: Long): List<DeviceInfo>
}

data class AppUsageBreakdown(
    val packageName: String,
    val totalSeconds: Long
)

data class SessionCountResult(
    val packageName: String,
    val count: Int
)

data class HourlyUsage(
    val hour: Int,
    val usageSeconds: Long
)

data class AppSessionStats(
    val count: Int,
    val avgSeconds: Long,
    val maxSeconds: Long,
    val minSeconds: Long
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String
)
