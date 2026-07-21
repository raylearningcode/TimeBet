package com.timebet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.timebet.app.core.database.entity.PredictionStatus
import com.timebet.app.core.database.entity.SportsPredictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SportsPredictionDao {

    @Insert
    suspend fun insert(prediction: SportsPredictionEntity): Long

    @Update
    suspend fun update(prediction: SportsPredictionEntity)

    @Query("SELECT * FROM sports_predictions ORDER BY placedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SportsPredictionEntity>>

    @Query("SELECT * FROM sports_predictions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SportsPredictionEntity?

    @Query("SELECT * FROM sports_predictions WHERE providerEventId = :eventId")
    suspend fun getByEvent(eventId: String): List<SportsPredictionEntity>

    @Query("""
        SELECT * FROM sports_predictions
        WHERE status IN (:statuses)
        ORDER BY placedAt DESC
    """)
    suspend fun getByStatuses(statuses: List<String>): List<SportsPredictionEntity>

    @Query("""
        SELECT * FROM sports_predictions
        WHERE status = :status
        ORDER BY placedAt DESC
    """)
    fun observeByStatus(status: String): Flow<List<SportsPredictionEntity>>

    @Query("""
        SELECT COALESCE(SUM(stakeSeconds), 0) FROM sports_predictions
        WHERE status IN ('pending_cancelable', 'pending_locked')
    """)
    suspend fun getActiveStakeTotal(): Long

    @Query("""
        SELECT * FROM sports_predictions
        WHERE status IN ('pending_cancelable', 'pending_locked')
        ORDER BY placedAt DESC
    """)
    fun observeActive(): Flow<List<SportsPredictionEntity>>

    @Query("""
        UPDATE sports_predictions
        SET status = :newStatus, lockedAt = :lockedAt
        WHERE status = :oldStatus AND placementLocalDate < :today
    """)
    suspend fun lockPastPredictions(
        oldStatus: String = PredictionStatus.PENDING_CANCELABLE,
        newStatus: String = PredictionStatus.PENDING_LOCKED,
        today: String,
        lockedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE sports_predictions
        SET status = :newStatus, settledAt = :settledAt, settlementProfitSeconds = :profit
        WHERE id = :id
    """)
    suspend fun settle(id: Long, newStatus: String, profit: Long, settledAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT COALESCE(SUM(settlementProfitSeconds), 0) FROM sports_predictions
        WHERE status = 'won'
          AND settledAt >= :startOfDay AND settledAt < :endOfDay
    """)
    suspend fun getDailySettledProfit(startOfDay: Long, endOfDay: Long): Long

    @Query("""
        SELECT COUNT(*) FROM sports_predictions
        WHERE placedAt >= :startOfDay AND placedAt < :endOfDay
    """)
    suspend fun getDailyCount(startOfDay: Long, endOfDay: Long): Int

    @Query("""
        SELECT COALESCE(SUM(stakeSeconds), 0) FROM sports_predictions
        WHERE placedAt >= :startOfDay AND placedAt < :endOfDay
    """)
    suspend fun getDailyStaked(startOfDay: Long, endOfDay: Long): Long
}
