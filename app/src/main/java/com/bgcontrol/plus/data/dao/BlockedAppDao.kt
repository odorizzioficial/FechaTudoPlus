package com.bgcontrol.plus.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bgcontrol.plus.data.entities.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAll(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps WHERE isEnabled = 1")
    suspend fun getEnabled(): List<BlockedAppEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName)")
    suspend fun exists(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<BlockedAppEntity>)

    @Query("UPDATE blocked_apps SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query(
        """
        UPDATE blocked_apps
        SET lastActionAt = :timestamp,
            lastActionSucceeded = :succeeded,
            lastReclaimedBytes = :reclaimed,
            totalReclaimedBytes = totalReclaimedBytes + :reclaimedIncrement
        WHERE packageName = :packageName
        """
    )
    suspend fun registerAction(
        packageName: String,
        timestamp: Long,
        succeeded: Boolean,
        reclaimed: Long?,
        reclaimedIncrement: Long
    )

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT SUM(totalReclaimedBytes) FROM blocked_apps")
    fun observeTotalReclaimed(): Flow<Long?>
}
