package com.timebet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.timebet.app.core.database.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)

    @Update
    suspend fun update(settings: UserSettingsEntity)

    @Query("UPDATE user_settings SET baseDailyAllowanceSeconds = :seconds, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateBaseAllowance(seconds: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET notificationsEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateNotifications(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET hapticsEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateHaptics(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET soundEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateSound(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())
}
