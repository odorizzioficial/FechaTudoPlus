package com.bgcontrol.plus.monitor

import android.content.Context
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.quick.QuickAccessService
import kotlinx.coroutines.flow.first

/**
 * Verifica as preferências do usuário e relança cada serviço essencial que
 * deveria estar rodando. Usado tanto pelo alarme periódico do KeepAlive
 * quanto pelo Cão de Guarda, que reage à tela ligando — os dois fazem
 * exatamente a mesma checagem, só que em momentos diferentes.
 */
suspend fun relancarServicosEssenciais(context: Context) {
    val app = context.applicationContext as BgControlApp

    // Reavalia o estado real do Shizuku a cada verificação, em vez de confiar
    // só nos avisos passivos dele (binder recebido/binder morto). Esses
    // avisos dependem de o próprio Shizuku notificar o app, e há relatos de
    // isso não acontecer de forma confiável em segundo plano com a tela
    // apagada — o app ficava preso mostrando Shizuku indisponível até o
    // usuário reabrir a tela principal manualmente. refresh() faz uma
    // consulta ao vivo (Shizuku.pingBinder()), então corrige sozinho
    // qualquer estado desatualizado, sem esperar por nenhum aviso.
    app.container.shizukuManager.refresh()

    // Esquenta a conexão com o Shizuku assim que ele é detectado como pronto,
    // em vez de esperar a primeira tentativa real de encerrar um app
    // bloqueado. Sem isso, exatamente essa primeira tentativa (o momento em
    // que o usuário testa se voltou a funcionar) é quem paga o preço de
    // reestabelecer o vínculo — o que explica a demora e as primeiras
    // tentativas falhando logo depois de desbloquear a tela. Rodar um
    // comando bem leve aqui, de antemão, faz esse vínculo já estar pronto
    // quando for realmente preciso.
    if (app.container.shizukuManager.isReady) {
        app.container.appMonitor.execPrivileged("true")
    }

    // O KeepAlive é a âncora: sem ele nenhum alarme futuro seria reagendado.
    KeepAliveService.start(context)

    val settings = app.container.settingsRepository.settings.first()

    if (settings.blockerServiceEnabled &&
        app.container.repository.enabledBlockedApps().isNotEmpty()
    ) {
        BlockedAppWatcherService.start(context)
    }
    if (settings.persistentNotification || settings.bubbleEnabled) {
        QuickAccessService.sincronizar(context, true)
    }
}
