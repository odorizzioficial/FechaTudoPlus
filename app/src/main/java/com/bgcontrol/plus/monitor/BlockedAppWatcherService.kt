package com.bgcontrol.plus.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.MainActivity
import com.bgcontrol.plus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

/**
 * Vigia os aplicativos bloqueados.
 *
 * Regra: enquanto o app bloqueado estiver em primeiro plano, nada acontece.
 * Assim que ele sai do primeiro plano, o serviço aguarda um período de carência
 * e tenta encerrá-lo de fato.
 *
 * Consumo: o laço só roda com a tela ligada e usa intervalo adaptativo
 * (rápido logo após uma troca de app, lento quando nada muda).
 */
class BlockedAppWatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    private lateinit var app: BgControlApp

    /** Pacote bloqueado -> instante da última tentativa de encerramento. */
    private val ultimaAcao = mutableMapOf<String, Long>()

    /** Quando a suspensão de todos os bloqueados foi reaplicada pela última vez. */
    private var ultimoReforco = 0L

    /**
     * Pacotes bloqueados que já foram confirmados em primeiro plano nesta
     * "sessão de abertura" — só esses podem ser encerrados por terem saído.
     * Ver o comentário completo em [tick].
     */
    private val jaEstiveAberto = mutableSetOf<String>()

    /**
     * Pacote bloqueado -> instante em que foi visto saindo de primeiro plano
     * pela última vez. Só é usado para apps com atraso configurado — mede
     * quanto tempo já passou desde a saída, para decidir se já é hora de
     * encerrar de verdade.
     */
    private val momentoSaida = mutableMapOf<String, Long>()

    /** Última leitura de primeiro plano, para detectar quando ela muda. */
    private var ultimaLeituraForeground: String? = null

    /**
     * Só é `true` no primeiro ciclo depois que este serviço (re)nasce — seja
     * pela primeira abertura, seja depois de ter sido morto pelo sistema e
     * relançado pelo Cão de Guarda. Ver o comentário completo em [tick].
     */
    private var primeiroCiclo = true

    override fun onCreate() {
        super.onCreate()
        app = application as BgControlApp
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            while (isActive) {
                if (!powerManager.isInteractive) {
                    // Tela desligada: nenhuma troca de primeiro plano acontece.
                    delay(SCREEN_OFF_INTERVAL_MS)
                    continue
                }
                val proximo = try {
                    tick()
                } catch (t: Throwable) {
                    IDLE_INTERVAL_MS
                }
                delay(proximo)
            }
        }
    }

    /** Devolve o intervalo até a próxima verificação. */
    private suspend fun tick(): Long {
        val blocked = app.container.repository.enabledBlockedApps()
        if (blocked.isEmpty()) {
            ultimaAcao.clear()
            jaEstiveAberto.clear()
            momentoSaida.clear()
            ultimaLeituraForeground = null
            return IDLE_INTERVAL_MS
        }

        val monitor = app.container.appMonitor
        val foreground = monitor.currentForegroundPackageRapido()
        val now = System.currentTimeMillis()
        var algumEmPrimeiroPlano = false

        // Detecta qualquer troca de app na tela (não só de bloqueados) para
        // acelerar a checagem por um instante — é isso que permite pegar a
        // confirmação de um app recém-aberto rápido, sem esperar o próximo
        // ciclo de 4 segundos do modo parado.
        val trocouAgora = foreground != ultimaLeituraForeground
        ultimaLeituraForeground = foreground

        // O bloqueio é uma suspensão, e o sistema a desfaz sozinho em algumas
        // situações — atualização pela loja, troca de usuário, certas ROMs.
        // Reaplicar de tempos em tempos é o que faz o bloqueio durar sem o
        // usuário precisar reabrir o aplicativo.
        val ativos = blocked.map { it.packageName }
        if (now - ultimoReforco > REFORCO_MS) {
            ultimoReforco = now
            monitor.reforcarBloqueios(ativos.filterNot { it == foreground })
        }

        for (entry in blocked) {
            val pkg = entry.packageName

            if (pkg == foreground) {
                // A leitura diz que este pacote está na tela — mas confere se
                // ele realmente tem um processo vivo antes de confiar nisso
                // cegamente.
                //
                // Motivo: arrastar um app pra fora direto na tela de
                // recentes (sem passar pela tela inicial) às vezes não gera
                // nenhum evento de troca de primeiro plano para o Android
                // registrar — tecnicamente nenhum outro app "assumiu" a
                // tela, só uma visualização por cima foi fechada. Nesse
                // caso, a leitura continua apontando para o app antigo
                // mesmo depois de a task dele já ter sido destruída de
                // verdade. Sem essa checagem, o vigia nunca perceberia a
                // saída nesse cenário específico, porque pra ele nunca
                // houve saída nenhuma.
                if (monitor.memoryOf(pkg) == null) {
                    jaEstiveAberto.remove(pkg)
                    continue
                }
                // Confirmado em primeiro plano de verdade: zera o histórico
                // de encerramento e marca que ele de fato abriu, para que
                // uma ausência futura seja tratada como saída de verdade.
                algumEmPrimeiroPlano = true
                ultimaAcao.remove(pkg)
                momentoSaida.remove(pkg)
                jaEstiveAberto.add(pkg)
                continue
            }

            if (pkg !in jaEstiveAberto) {
                // A leitura de primeiro plano não bateu com este pacote, mas
                // nunca vimos ele confirmadamente aberto ainda — pode estar
                // no meio de abrir. Só espera a próxima leitura, sem agir.
                //
                // EXCEÇÃO — só no primeiro ciclo depois que este serviço
                // (re)nasce: confere a memória uma única vez. Se o sistema
                // matou o vigia (o Cão de Guarda relança ele do zero, sem
                // nenhuma lembrança de nada) enquanto um app bloqueado já
                // estava rodando escondido, esse app nunca teria como entrar
                // em jaEstiveAberto sem essa checagem — ele nunca "abriu" na
                // frente deste vigia específico, já estava aberto antes dele
                // nascer. Diferente de checar memória em todo ciclo (o bug
                // já corrigido antes, que sabotava a detecção de saída), essa
                // checagem acontece uma vez só, e junto com uma leitura de
                // primeiro plano que já disse "não é este app" — sem a
                // ambiguidade do momento de abertura.
                if (primeiroCiclo && monitor.memoryOf(pkg) != null) {
                    jaEstiveAberto.add(pkg)
                } else {
                    continue
                }
            }

            // Já foi confirmado aberto antes e agora não bate mais com a
            // leitura de primeiro plano: saída de verdade, sem ambiguidade.
            //
            // Se o usuário configurou um atraso para este app (aba
            // Bloqueados, arrastar o cartão para o lado), a saída não
            // encerra na hora — só depois que o tempo combinado passar. Se
            // ele voltar ao app antes disso, o momentoSaida é limpo lá em
            // cima e a contagem recomeça do zero na próxima vez que sair de
            // novo.
            val atrasoSegundos = entry.delaySeconds ?: 0
            if (atrasoSegundos > 0) {
                val saidaRegistradaEm = momentoSaida.getOrPut(pkg) { now }
                if (now - saidaRegistradaEm < atrasoSegundos * 1_000L) {
                    continue
                }
            }

            val ultima = ultimaAcao[pkg]
            if (ultima != null && now - ultima < REPETICAO_MS) continue

            val reclaimed = monitor.memoryOf(pkg)
            val stopped = monitor.stopApp(pkg)
            if (stopped) {
                // Confirmado fora: só volta a ser elegível para encerramento
                // automático na próxima vez que for visto em primeiro plano.
                jaEstiveAberto.remove(pkg)
                momentoSaida.remove(pkg)
            } else {
                // Resistiu ao encerramento — alguns apps religam um processo
                // de segundo plano sozinhos logo em seguida (comum em
                // navegadores, com serviços de notificação/sincronização).
                //
                // BUG CORRIGIDO: antes, uma tentativa que falhasse removia o
                // pacote de jaEstiveAberto de qualquer forma. Como reentrar
                // nesse conjunto exige vê-lo em primeiro plano de novo — o que
                // nunca acontece para um app minimizado — o vigia desistia
                // de vez após a primeira falha, mesmo o app continuando
                // rodando escondido. Mantendo-o aqui, a nova tentativa
                // agendada logo abaixo (poucos segundos depois) continua
                // valendo sem precisar de outro evento de primeiro plano.
                monitor.aplicarBloqueio(pkg)
            }
            app.container.repository.registerBlockedAction(pkg, stopped, reclaimed)
            ultimaAcao[pkg] = if (stopped) now else now - REPETICAO_MS + 3_000L
        }

        primeiroCiclo = false

        // Rápido enquanto algo estiver aberto OU logo depois de qualquer troca
        // de app na tela — essa janela rápida é o que permite confirmar um
        // app recém-aberto sem cair no ritmo de 4 segundos do modo parado.
        return if (algumEmPrimeiroPlano || trocouAgora) FAST_INTERVAL_MS else IDLE_INTERVAL_MS
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watcher_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.watcher_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watcher_notification_title))
            .setContentText(getString(R.string.watcher_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "blocked_watcher"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.bgcontrol.plus.STOP_WATCHER"

        /** Ritmo enquanto um app bloqueado está aberto: encerra assim que ele sai. */
        private const val FAST_INTERVAL_MS = 200L

        /** Ritmo quando nenhum app bloqueado está em uso. */
        private const val IDLE_INTERVAL_MS = 4_000L

        /** Com a tela apagada não há troca de primeiro plano para observar. */
        private const val SCREEN_OFF_INTERVAL_MS = 60_000L

        /** Evita repetir o force-stop no mesmo app em sequência. */
        private const val REPETICAO_MS = 15_000L

        /** Intervalo entre reaplicações da suspensão dos bloqueados. */
        private const val REFORCO_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, BlockedAppWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockedAppWatcherService::class.java))
        }
    }
}
