package com.bgcontrol.plus.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgcontrol.plus.data.entities.ScheduleAppEntity
import com.bgcontrol.plus.data.entities.ScheduleGroupEntity
import com.bgcontrol.plus.data.entities.ScheduleMode
import com.bgcontrol.plus.data.repository.ScheduleRepository
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.schedule.ScheduleAlarms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val groups: List<ScheduleGroupEntity> = emptyList(),
    val appsByGroup: Map<Long, List<ScheduleAppEntity>> = emptyMap(),
    val exactAlarmsAllowed: Boolean = true
)

class ScheduleViewModel(
    application: Application,
    private val repository: ScheduleRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.groups, repository.apps) { grupos, apps ->
                grupos to apps.groupBy { it.groupId }
            }.collect { (grupos, apps) ->
                _uiState.value = _uiState.value.copy(groups = grupos, appsByGroup = apps)
            }
        }
        refreshAlarmPermission()
    }

    /** O Android 12+ pode negar alarmes exatos; a tela avisa quando isso acontece. */
    fun refreshAlarmPermission() {
        val context = getApplication<Application>()
        val permitido = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(exactAlarmsAllowed = permitido)
    }

    fun createGroup(
        name: String,
        mode: ScheduleMode,
        hour: Int,
        minute: Int,
        second: Int,
        daysOfWeek: Int,
        repeatEnabled: Boolean,
        runAtDate: Long?,
        intervalSeconds: Int?,
        apps: List<InstalledApp> = emptyList()
    ) {
        viewModelScope.launch {
            val grupo = ScheduleGroupEntity(
                name = name.ifBlank { mode.name },
                mode = mode.name,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                second = second.coerceIn(0, 59),
                daysOfWeek = daysOfWeek,
                repeatEnabled = repeatEnabled,
                runAtDate = runAtDate,
                intervalSeconds = intervalSeconds
            )
            val id = repository.createGroup(grupo)
            if (apps.isNotEmpty()) repository.addApps(id, apps)
            ScheduleAlarms.reschedule(getApplication(), grupo.copy(id = id))
        }
    }

    /** Salva as mudanças de um grupo existente, sem criar outro. */
    fun updateGroup(
        original: ScheduleGroupEntity,
        name: String,
        mode: ScheduleMode,
        hour: Int,
        minute: Int,
        second: Int,
        daysOfWeek: Int,
        repeatEnabled: Boolean,
        runAtDate: Long?,
        intervalSeconds: Int?,
        apps: List<InstalledApp>?
    ) {
        viewModelScope.launch {
            val atualizado = original.copy(
                name = name.ifBlank { original.name },
                mode = mode.name,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                second = second.coerceIn(0, 59),
                daysOfWeek = daysOfWeek,
                repeatEnabled = repeatEnabled,
                runAtDate = runAtDate,
                intervalSeconds = intervalSeconds,
                isEnabled = true
            )
            repository.updateGroup(atualizado)
            if (apps != null) repository.replaceApps(original.id, apps)
            ScheduleAlarms.reschedule(getApplication(), atualizado)
        }
    }

    fun addApps(groupId: Long, apps: List<InstalledApp>) {
        viewModelScope.launch { repository.addApps(groupId, apps) }
    }

    fun removeApp(groupId: Long, packageName: String) {
        viewModelScope.launch { repository.removeApp(groupId, packageName) }
    }

    fun setEnabled(group: ScheduleGroupEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(group.id, enabled)
            ScheduleAlarms.reschedule(getApplication(), group.copy(isEnabled = enabled))
        }
    }

    fun deleteGroup(group: ScheduleGroupEntity) {
        viewModelScope.launch {
            ScheduleAlarms.cancel(getApplication(), group.id)
            repository.deleteGroup(group.id)
        }
    }

    fun nextRun(group: ScheduleGroupEntity): Long = ScheduleAlarms.nextTrigger(group)
}
