package com.bgcontrol.plus.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.quick.QuickAccessService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receptor do alarme periódico do KeepAliveService.
 *
 * Quando o alarme dispara, este receptor verifica se os serviços essenciais
 * ainda estão rodando e os reinicia se necessário. Depois reagenda o próximo
 * alarme para que o ciclo continue indefinidamente.
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BgControlApp
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Sempre reinicia o KeepAlive — é ele que garante que este
                // próprio alarme continuará sendo reagendado.
                KeepAliveService.start(context)

                val settings = app.container.settingsRepository.settings.first()

                // Religa os serviços que dependem de preferências do usuário.
                if (settings.blockerServiceEnabled &&
                    app.container.repository.enabledBlockedApps().isNotEmpty()
                ) {
                    BlockedAppWatcherService.start(context)
                }
                if (settings.persistentNotification || settings.bubbleEnabled) {
                    QuickAccessService.sincronizar(context, true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
