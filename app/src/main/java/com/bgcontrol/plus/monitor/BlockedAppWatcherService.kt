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
            return IDLE_INTERVAL_MS
        }

        val monitor = app.container.appMonitor
        val foreground = monitor.currentForegroundPackage()
        val now = System.currentTimeMillis()
        var algumEmPrimeiroPlano = false

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
                // Em primeiro plano o app funciona normalmente; zera o histórico
                // para que ele seja encerrado assim que sair.
                algumEmPrimeiroPlano = true
                ultimaAcao.remove(pkg)
                continue
            }

            // Saiu do primeiro plano: encerra imediatamente, sem carência.
            val ultima = ultimaAcao[pkg]
            if (ultima != null && now - ultima < REPETICAO_MS) continue

            val reclaimed = monitor.memoryOf(pkg)
            val stopped = monitor.stopApp(pkg)
            if (!stopped) {
                // Resistiu ao encerramento: reaplica o bloqueio inteiro em vez
                // de só restringir o segundo plano. Apps como o Play Services
                // voltam justamente porque o force-stop sozinho não os segura.
                monitor.aplicarBloqueio(pkg)
            }
            app.container.repository.registerBlockedAction(pkg, stopped, reclaimed)
            ultimaAcao[pkg] = if (stopped) now else now - REPETICAO_MS + 3_000L
        }

        // Enquanto um app bloqueado está aberto, a verificação fica rápida para
        // encerrá-lo no instante em que o usuário sair. Parado, o ritmo cai.
        return if (algumEmPrimeiroPlano) FAST_INTERVAL_MS else IDLE_INTERVAL_MS
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
        private const val FAST_INTERVAL_MS = 120L

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
