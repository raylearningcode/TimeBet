package com.timebet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.timebet.app.core.database.entity.CasinoRoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CasinoRoundDao {

    @Insert
    suspend fun insert(round: CasinoRoundEntity): Long

    @Update
    suspend fun update(round: CasinoRoundEntity)

    @Query("SELECT * FROM casino_rounds WHERE status = 'initiated'")
    suspend fun getUnsettledRounds(): List<CasinoRoundEntity>

    @Query("SELECT * FROM casino_rounds ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<CasinoRoundEntity>>

    @Query("""
        SELECT * FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
        ORDER BY startedAt DESC
    """)
    suspend fun getByDateRange(startOfDay: Long, endOfDay: Long): List<CasinoRoundEntity>

    @Query("""
        SELECT gameType, COUNT(*) as count
        FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY gameType
    """)
    suspend fun getGameCounts(startOfDay: Long, endOfDay: Long): List<GameCountResult>

    @Query("""
        SELECT COALESCE(SUM(stakeSeconds), 0) FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getTotalWagered(startOfDay: Long, endOfDay: Long): Long

    @Query("""
        SELECT COALESCE(SUM(profitSeconds), 0) FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getTotalProfit(startOfDay: Long, endOfDay: Long): Long

    @Query("""
        SELECT COALESCE(SUM(lossSeconds), 0) FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getTotalLoss(startOfDay: Long, endOfDay: Long): Long

    @Query("""
        SELECT COUNT(*) FROM casino_rounds
        WHERE result = 'win'
          AND startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getWinCount(startOfDay: Long, endOfDay: Long): Int

    @Query("""
        SELECT COUNT(*) FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getTotalCount(startOfDay: Long, endOfDay: Long): Int

    @Query("""
        SELECT COALESCE(MAX(profitSeconds), 0) FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getLargestWin(startOfDay: Long, endOfDay: Long): Long

    @Query("""
        SELECT COALESCE(MAX(lossSeconds), 0) FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getLargestLoss(startOfDay: Long, endOfDay: Long): Long

    @Query("""
        SELECT gameType, COUNT(*) as count
        FROM casino_rounds
        WHERE startedAt >= :startOfDay AND startedAt < :endOfDay
        GROUP BY gameType
        ORDER BY count DESC
        LIMIT 1
    """)
    suspend fun getMostPlayedGame(startOfDay: Long, endOfDay: Long): GameCountResult?

    @Query("SELECT * FROM casino_rounds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CasinoRoundEntity?
}

data class GameCountResult(
    val gameType: String,
    val count: Int
)
