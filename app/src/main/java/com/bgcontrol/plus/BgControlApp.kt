package com.bgcontrol.plus

import android.app.Application
import com.bgcontrol.plus.monitor.BlockedAppWatcherService
import com.bgcontrol.plus.monitor.KeepAliveService
import com.bgcontrol.plus.quick.QuickAccessService
import com.bgcontrol.plus.schedule.ScheduleAlarms
import com.bgcontrol.plus.util.DefaultProtectedApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BgControlApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Escopo de vida longa: sobrevive a telas e serviços de curta duração. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val scope get() = appScope

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.shizukuManager.register()

        // Inicia o serviço de persistência imediatamente, antes de qualquer
        // outra coisa. Ele é a âncora que mantém o processo vivo mesmo quando
        // o usuário fecha o aplicativo ou o sistema tenta libertar memória.
        KeepAliveService.start(this)

        // Quem encerra aplicativos precisa saber quais estão bloqueados, senão
        // o passo que limpa a tela de recentes libera o pacote logo depois de
        // bloqueá-lo. Este é o único ponto que mantém essa lista em dia, e ele
        // vale para o botão da tela, a bolha, o bloco rápido e o agendamento.
        scope.launch {
            container.repository.blockedApps
                .map { lista ->
                    lista.filter { it.isEnabled }.map { it.packageName }.toSet()
                }
                .collect { container.appMonitor.pacotesBloqueados = it }
        }

        scope.launch {
            val settings = container.settingsRepository.settings.first()
            // Primeira execução: protege os aplicativos padrão do aparelho para
            // que "Fechar Tudo" nunca derrube tela inicial, discador, teclado etc.
            if (!settings.defaultsSeeded) {
                val padroes = DefaultProtectedApps.detect(this@BgControlApp)
                if (padroes.isNotEmpty()) container.repository.addRestricted(padroes)
                container.settingsRepository.setDefaultsSeeded(true)
            }

            // Os alarmes vivem no sistema, não no app: reancorá-los na abertura
            // cobre o caso de o processo ter sido morto entre uma execução e outra.
            container.scheduleRepository.allGroups().forEach {
                ScheduleAlarms.reschedule(this@BgControlApp, it)
            }

            // Notificação fixa e bolha voltam a subir após reiniciar o processo.
            if (settings.persistentNotification || settings.bubbleEnabled) {
                QuickAccessService.sincronizar(this@BgControlApp, true)
            }

            if (settings.blockerServiceEnabled &&
                container.repository.enabledBlockedApps().isNotEmpty()
            ) {
                BlockedAppWatcherService.start(this@BgControlApp)
            }
        }
    }

    override fun onTerminate() {
        container.shizukuManager.unregister()
        container.shizukuManager.unbind()
        super.onTerminate()
    }
}
