package com.timebet.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.timebet.app.core.database.dao.AppUsageSessionDao
import com.timebet.app.core.database.dao.CasinoRoundDao
import com.timebet.app.core.database.dao.ControlledAppDao
import com.timebet.app.core.database.dao.DailyTimeBankDao
import com.timebet.app.core.database.dao.DailyUsageAggregateDao
import com.timebet.app.core.database.dao.SportsPredictionDao
import com.timebet.app.core.database.dao.UserSettingsDao
import com.timebet.app.core.database.entity.AppUsageSessionEntity
import com.timebet.app.core.database.entity.CasinoRoundEntity
import com.timebet.app.core.database.entity.ControlledAppEntity
import com.timebet.app.core.database.entity.DailyTimeBankEntity
import com.timebet.app.core.database.entity.DailyUsageAggregateEntity
import com.timebet.app.core.database.entity.SportsPredictionEntity
import com.timebet.app.core.database.entity.UserSettingsEntity

@Database(
    entities = [
        UserSettingsEntity::class,
        ControlledAppEntity::class,
        DailyTimeBankEntity::class,
        AppUsageSessionEntity::class,
        CasinoRoundEntity::class,
        SportsPredictionEntity::class,
        DailyUsageAggregateEntity::class
    ],
    version = 6,  // bumped from 5
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun controlledAppDao(): ControlledAppDao
    abstract fun dailyTimeBankDao(): DailyTimeBankDao
    abstract fun appUsageSessionDao(): AppUsageSessionDao
    abstract fun casinoRoundDao(): CasinoRoundDao
    abstract fun sportsPredictionDao(): SportsPredictionDao
    abstract fun dailyUsageAggregateDao(): DailyUsageAggregateDao

    companion object {
        private const val DATABASE_NAME = "timebet.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun create(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
