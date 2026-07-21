package com.timebet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timebet.app.core.database.entity.DailyTimeBankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTimeBankDao {

    @Query("SELECT * FROM daily_time_bank WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyTimeBankEntity?>

    @Query("SELECT * FROM daily_time_bank WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyTimeBankEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bank: DailyTimeBankEntity)

    @Query("""
        UPDATE daily_time_bank
        SET currentBalanceSeconds = :balance,
            casinoProfitSeconds = :casinoProfit,
            casinoLossSeconds = :casinoLoss,
            sportsProfitSeconds = :sportsProfit,
            totalWinSeconds = :totalWinSeconds,
            usedSeconds = :used,
            updatedAt = :updatedAt
        WHERE date = :date
    """)
    suspend fun updateBalances(
        date: String,
        balance: Long,
        casinoProfit: Long,
        casinoLoss: Long,
        sportsProfit: Long,
        totalWinSeconds: Long,
        used: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE daily_time_bank
        SET currentBalanceSeconds = :balance, updatedAt = :updatedAt
        WHERE date = :date
    """)
    suspend fun updateBalance(date: String, balance: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM daily_time_bank WHERE date >= :fromDate AND date <= :toDate ORDER BY date ASC")
    suspend fun getRange(fromDate: String, toDate: String): List<DailyTimeBankEntity>

    @Query("SELECT * FROM daily_time_bank ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<DailyTimeBankEntity>
}
