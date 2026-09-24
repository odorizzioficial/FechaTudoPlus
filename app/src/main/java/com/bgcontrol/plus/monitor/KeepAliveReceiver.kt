package com.bgcontrol.plus.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                relancarServicosEssenciais(context)
            } finally {
                pending.finish()
            }
        }
    }
}
