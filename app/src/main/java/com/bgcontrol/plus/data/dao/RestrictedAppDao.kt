package com.bgcontrol.plus.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bgcontrol.plus.data.entities.RestrictedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestrictedAppDao {

    @Query("SELECT * FROM restricted_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RestrictedAppEntity>>

    @Query("SELECT * FROM restricted_apps")
    suspend fun getAll(): List<RestrictedAppEntity>

    @Query("SELECT packageName FROM restricted_apps WHERE isEnabled = 1")
    suspend fun getEnabledPackages(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM restricted_apps WHERE packageName = :packageName)")
    suspend fun exists(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: RestrictedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<RestrictedAppEntity>)

    @Query("UPDATE restricted_apps SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("DELETE FROM restricted_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
