package com.timebet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timebet.app.core.database.entity.DailyUsageAggregateEntity

@Dao
interface DailyUsageAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: DailyUsageAggregateEntity)

    @Query("""
        SELECT * FROM daily_usage_aggregates
        WHERE date = :date
        ORDER BY usageSeconds DESC
    """)
    suspend fun getByDate(date: String): List<DailyUsageAggregateEntity>

    @Query("""
        SELECT * FROM daily_usage_aggregates
        WHERE date = :date AND packageName = :packageName
        LIMIT 1
    """)
    suspend fun getByDateAndPackage(date: String, packageName: String): DailyUsageAggregateEntity?

    @Query("""
        SELECT * FROM daily_usage_aggregates
        WHERE date >= :fromDate AND date <= :toDate AND packageName = :packageName
        ORDER BY date ASC
    """)
    suspend fun getRangeForApp(fromDate: String, toDate: String, packageName: String): List<DailyUsageAggregateEntity>

    @Query("""
        SELECT COALESCE(SUM(usageSeconds), 0) FROM daily_usage_aggregates
        WHERE date >= :fromDate AND date <= :toDate
    """)
    suspend fun getTotalUsage(fromDate: String, toDate: String): Long

    @Query("DELETE FROM daily_usage_aggregates WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    @Query("""
        SELECT date, SUM(usageSeconds) as totalSeconds
        FROM daily_usage_aggregates
        GROUP BY date ORDER BY date DESC LIMIT :limit
    """)
    suspend fun getDailyTotals(limit: Int = 30): List<DailyTotal>
}

data class DailyTotal(
    val date: String,
    val totalSeconds: Long
)
