package com.timebet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timebet.app.core.database.entity.ControlledAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ControlledAppDao {

    @Query("SELECT * FROM controlled_apps ORDER BY appName ASC")
    fun observeAll(): Flow<List<ControlledAppEntity>>

    @Query("SELECT * FROM controlled_apps WHERE isControlled = 1 ORDER BY appName ASC")
    fun observeControlled(): Flow<List<ControlledAppEntity>>

    @Query("SELECT * FROM controlled_apps ORDER BY appName ASC")
    suspend fun getAll(): List<ControlledAppEntity>

    @Query("SELECT * FROM controlled_apps WHERE isControlled = 1")
    suspend fun getControlled(): List<ControlledAppEntity>

    @Query("SELECT * FROM controlled_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): ControlledAppEntity?

    @Query("SELECT * FROM controlled_apps WHERE packageName = :packageName LIMIT 1")
    fun observeByPackage(packageName: String): Flow<ControlledAppEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: ControlledAppEntity)

    @Query("UPDATE controlled_apps SET isControlled = :controlled, updatedAt = :updatedAt WHERE packageName = :packageName")
    suspend fun setControlled(packageName: String, controlled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM controlled_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT packageName FROM controlled_apps WHERE isControlled = 1")
    suspend fun getControlledPackages(): List<String>
}
