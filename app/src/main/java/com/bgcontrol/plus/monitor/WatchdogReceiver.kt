package com.bgcontrol.plus.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.bgcontrol.plus.BgControlApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Modo Cão de Guarda.
 *
 * O alarme periódico do KeepAlive checa os serviços a cada 1 minuto — bom
 * o bastante no dia a dia, mas lento demais para o caso específico que deu
 * origem a isto: em certas versões do One UI (relatado em Android 13), o
 * sistema mata os serviços em segundo plano ao apagar a tela, e sem nada
 * cutucando o app, o bloqueio ficava sem efeito até o próximo alarme.
 *
 * `ACTION_SCREEN_ON` e `ACTION_USER_PRESENT` não podem ser declarados no
 * AndroidManifest a partir do Android 8 — são "broadcasts implícitos"
 * bloqueados para receptores estáticos. Por isso este receptor é registrado
 * em tempo de execução, uma única vez, quando o processo do app sobe (ver
 * [registrar]), e vale por toda a vida do processo.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as BgControlApp
                val settings = app.container.settingsRepository.settings.first()
                if (settings.watchdogEnabled) {
                    relancarServicosEssenciais(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private var registrado = false

        /**
         * Registra o receptor uma única vez por processo. Chamar de novo não
         * duplica nada — a proteção por [registrado] garante isso mesmo se
         * [com.bgcontrol.plus.BgControlApp] for recriado sem o processo
         * inteiro reiniciar.
         */
        fun registrar(context: Context) {
            if (registrado) return
            registrado = true
            val filtro = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            val app = context.applicationContext
            // A partir do Android 13, registrar um receptor em tempo de
            // execução exige dizer explicitamente se ele aceita broadcasts de
            // outros apps ou só do próprio sistema — sem isso o registro
            // lança uma exceção e derruba o app na inicialização. Como
            // ACTION_SCREEN_ON e ACTION_USER_PRESENT só vêm do sistema
            // mesmo, RECEIVER_NOT_EXPORTED é o valor correto.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(
                    WatchdogReceiver(), filtro, Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                app.registerReceiver(WatchdogReceiver(), filtro)
            }
        }
    }
}
