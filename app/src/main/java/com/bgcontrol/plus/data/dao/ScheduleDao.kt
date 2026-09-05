package com.bgcontrol.plus.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bgcontrol.plus.data.entities.ScheduleAppEntity
import com.bgcontrol.plus.data.entities.ScheduleGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_groups ORDER BY hour, minute, second")
    fun observeGroups(): Flow<List<ScheduleGroupEntity>>

    @Query("SELECT * FROM schedule_apps")
    fun observeApps(): Flow<List<ScheduleAppEntity>>

    @Query("SELECT * FROM schedule_groups")
    suspend fun getGroups(): List<ScheduleGroupEntity>

    @Query("SELECT * FROM schedule_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: Long): ScheduleGroupEntity?

    @Query("SELECT * FROM schedule_apps WHERE groupId = :groupId")
    suspend fun getApps(groupId: Long): List<ScheduleAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ScheduleGroupEntity): Long

    @Update
    suspend fun updateGroup(group: ScheduleGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<ScheduleAppEntity>)

    @Query("UPDATE schedule_groups SET isEnabled = :enabled WHERE id = :groupId")
    suspend fun setEnabled(groupId: Long, enabled: Boolean)

    @Query("UPDATE schedule_groups SET lastRunAt = :timestamp WHERE id = :groupId")
    suspend fun registerRun(groupId: Long, timestamp: Long)

    @Query("DELETE FROM schedule_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM schedule_apps WHERE groupId = :groupId")
    suspend fun deleteAppsOfGroup(groupId: Long)

    @Query("DELETE FROM schedule_apps WHERE groupId = :groupId AND packageName = :packageName")
    suspend fun deleteApp(groupId: Long, packageName: String)
}
