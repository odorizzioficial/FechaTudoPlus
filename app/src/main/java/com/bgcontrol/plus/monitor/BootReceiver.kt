package com.bgcontrol.plus.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.schedule.ScheduleAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Reativa o vigia após reiniciar o aparelho, se o usuário tiver deixado ligado. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as BgControlApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = app.container.settingsRepository.settings.first()
                val hasBlocked = app.container.repository.enabledBlockedApps().isNotEmpty()
                if (settings.blockerServiceEnabled && hasBlocked) {
                    BlockedAppWatcherService.start(context)
                }
                // Reiniciar o aparelho apaga os alarmes: é preciso registrá-los de novo.
                app.container.scheduleRepository.allGroups().forEach {
                    ScheduleAlarms.reschedule(context, it)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
