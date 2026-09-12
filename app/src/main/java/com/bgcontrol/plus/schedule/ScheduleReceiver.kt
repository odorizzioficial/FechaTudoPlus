package com.bgcontrol.plus.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.data.entities.ScheduleMode
import com.bgcontrol.plus.data.entities.FrozenAppEntity
import com.bgcontrol.plus.model.InstalledApp
import kotlinx.coroutines.launch

/**
 * Executa um grupo quando o alarme dispara e agenda a próxima ocorrência.
 * Os aplicativos protegidos continuam intocados: um grupo nunca encerra algo
 * que esteja na lista de Restritos.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleAlarms.ACTION_RUN) return
        val groupId = intent.getLongExtra(ScheduleAlarms.EXTRA_GROUP_ID, -1L)
        if (groupId < 0) return

        val app = context.applicationContext as BgControlApp
        val pending = goAsync()

        app.appScope.launch {
            try {
                val grupo = app.container.scheduleRepository.group(groupId) ?: return@launch
                if (!grupo.isEnabled) return@launch

                val apps = app.container.scheduleRepository.appsOf(groupId)
                if (apps.isEmpty()) return@launch

                val monitor = app.container.appMonitor
                val repository = app.container.repository
                val protegidos = repository.protectedPackages()

                when (grupo.scheduleMode) {
                    ScheduleMode.FORCE_STOP -> {
                        val alvos = apps.map { it.packageName }.filterNot { it in protegidos }
                        monitor.stopApps(alvos)
                    }

                    ScheduleMode.BLOCK -> apps.forEach {
                        repository.addBlocked(InstalledApp(it.packageName, it.appName))
                        monitor.setBackgroundRestricted(it.packageName, true)
                        monitor.stopApp(it.packageName)
                    }

                    ScheduleMode.RESTRICT -> apps.forEach {
                        repository.addRestricted(InstalledApp(it.packageName, it.appName))
                    }

                    ScheduleMode.FREEZE -> apps.forEach {
                        val cmd = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            "pm disable-user --user 0 ${it.packageName}"
                        } else {
                            "pm disable ${it.packageName}"
                        }
                        monitor.execPrivileged(cmd)
                        app.container.db.frozenAppDao()
                            .insert(FrozenAppEntity(it.packageName, it.appName))
                    }
                }

                app.container.scheduleRepository.registerRun(groupId)

                if (grupo.repeatEnabled) {
                    ScheduleAlarms.reschedule(context, grupo)
                } else {
                    // Grupo de uma vez só: cumpriu o papel e se desativa.
                    app.container.scheduleRepository.setEnabled(groupId, false)
                    ScheduleAlarms.cancel(context, groupId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
