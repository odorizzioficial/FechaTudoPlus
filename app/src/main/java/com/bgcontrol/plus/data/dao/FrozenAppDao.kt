package com.bgcontrol.plus.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bgcontrol.plus.data.entities.FrozenAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FrozenAppDao {
    @Query("SELECT * FROM frozen_apps ORDER BY appName COLLATE NOCASE")
    fun all(): Flow<List<FrozenAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: FrozenAppEntity)

    @Query("DELETE FROM frozen_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("SELECT packageName FROM frozen_apps")
    suspend fun allPackages(): List<String>

    @Query("UPDATE frozen_apps SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)
}
