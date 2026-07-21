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
}
