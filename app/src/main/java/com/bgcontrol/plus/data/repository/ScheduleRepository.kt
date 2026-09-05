package com.bgcontrol.plus.data.repository

import com.bgcontrol.plus.data.dao.ScheduleDao
import com.bgcontrol.plus.data.entities.ScheduleAppEntity
import com.bgcontrol.plus.data.entities.ScheduleGroupEntity
import com.bgcontrol.plus.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/** Grupos de limpeza agendada e os aplicativos de cada um. */
class ScheduleRepository(private val dao: ScheduleDao) {

    val groups: Flow<List<ScheduleGroupEntity>> = dao.observeGroups()
    val apps: Flow<List<ScheduleAppEntity>> = dao.observeApps()

    suspend fun allGroups(): List<ScheduleGroupEntity> = dao.getGroups()

    suspend fun group(groupId: Long): ScheduleGroupEntity? = dao.getGroup(groupId)

    suspend fun appsOf(groupId: Long): List<ScheduleAppEntity> = dao.getApps(groupId)

    suspend fun createGroup(group: ScheduleGroupEntity): Long = dao.insertGroup(group)

    suspend fun updateGroup(group: ScheduleGroupEntity) = dao.updateGroup(group)

    /** Troca por completo os aplicativos de um grupo. */
    suspend fun replaceApps(groupId: Long, apps: List<InstalledApp>) {
        dao.deleteAppsOfGroup(groupId)
        addApps(groupId, apps)
    }

    suspend fun addApps(groupId: Long, apps: List<InstalledApp>) = dao.insertApps(
        apps.map {
            ScheduleAppEntity(
                groupId = groupId,
                packageName = it.packageName,
                appName = it.appName
            )
        }
    )

    suspend fun removeApp(groupId: Long, packageName: String) = dao.deleteApp(groupId, packageName)

    suspend fun setEnabled(groupId: Long, enabled: Boolean) = dao.setEnabled(groupId, enabled)

    suspend fun registerRun(groupId: Long) =
        dao.registerRun(groupId, System.currentTimeMillis())

    suspend fun deleteGroup(groupId: Long) {
        dao.deleteAppsOfGroup(groupId)
        dao.deleteGroup(groupId)
    }
}
