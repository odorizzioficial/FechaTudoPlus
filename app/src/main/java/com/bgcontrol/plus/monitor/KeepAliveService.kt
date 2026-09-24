package com.bgcontrol.plus.monitor

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço que mantém o processo do aplicativo vivo em segundo plano — o
 * "Modo Cão de Guarda".
 *
 * A diferença entre um watchdog de verdade e uma checagem periódica é
 * simples: um watchdog fica olhando o tempo todo, em ciclos curtos, enquanto
 * está de pé — não espera um alarme, nem um evento específico do sistema
 * (que pode variar de fabricante pra fabricante). Esse laço curto é o que
 * funciona igual em qualquer Android, sem depender de nenhum truque de
 * marca específica.
 *
 * Quatro camadas, da mais forte pra mais fraca:
 *  1. Laço interno de ~15 segundos, enquanto este serviço está vivo —
 *     detecta e relança os outros serviços quase na hora.
 *  2. `onTaskRemoved`: se o usuário arrastar o próprio Fecha Tudo Plus pra
 *     fora dos recentes, relança tudo imediatamente, sem esperar o laço.
 *  3. START_STICKY — o sistema recria o serviço se o processo for morto
 *     (cobre o caso do próprio laço ser interrompido).
 *  4. Alarme de reforço a cada 1 minuto — rede de segurança para quando
 *     até este serviço for encerrado por completo e o START_STICKY demorar.
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var laco: Job? = null

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        startForeground(NOTIFICACAO_ID, montarNotificacao())
        agendarVigilancia(this)
        iniciarLaco()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        agendarVigilancia(this)
        if (laco == null || laco?.isActive != true) iniciarLaco()
        return START_STICKY
    }

    /**
     * Laço curto que roda enquanto o serviço estiver vivo. Só age quando o
     * Modo Cão de Guarda está ligado — desligado nas Configurações, o
     * serviço continua de pé (mantendo o processo vivo), mas para de checar
     * e relançar os outros serviços sozinho.
     */
    private fun iniciarLaco() {
        laco?.cancel()
        laco = scope.launch {
            while (isActive) {
                runCatching {
                    val app = applicationContext as BgControlApp
                    val ligado = app.container.settingsRepository.settings.first().watchdogEnabled
                    if (ligado) relancarServicosEssenciais(applicationContext)
                }
                delay(LACO_MS)
            }
        }
    }

    /**
     * Se o usuário arrastar o próprio Fecha Tudo Plus pra fora da tela de
     * recentes, o sistema pode congelar ou matar o processo logo em seguida.
     * Relançar aqui, na hora, cobre exatamente essa janela — sem isso, só o
     * próximo START_STICKY ou o alarme de 1 minuto pegariam essa falha.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val reiniciar = Intent(applicationContext, KeepAliveService::class.java)
        val pending = PendingIntent.getService(
            applicationContext,
            REINICIO_REQUEST_CODE,
            reiniciar,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 500L,
                pending
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        laco?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                getString(R.string.keepalive_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                description = getString(R.string.keepalive_channel_desc)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }
    }

    private fun montarNotificacao(): Notification =
        NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.keepalive_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    companion object {
        private const val CANAL_ID = "keep_alive"
        private const val NOTIFICACAO_ID = 3001

        /** Ritmo do laço interno do Cão de Guarda enquanto o serviço está vivo. */
        private const val LACO_MS = 3_000L

        // A isenção de bateria (pedida no onboarding) livra o app do limite
        // que o Android normalmente impõe a alarmes exatos repetidos em
        // segundo plano — sem essa isenção, o sistema não deixaria repetir
        // com menos de uns 9-10 minutos de intervalo, não importa o valor
        // pedido aqui.
        private const val INTERVALO_MS = 60_000L
        private const val ALARME_REQUEST_CODE = 9001
        private const val REINICIO_REQUEST_CODE = 9002

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun agendarVigilancia(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                ALARME_REQUEST_CODE,
                Intent(context, KeepAliveReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val proximo = SystemClock.elapsedRealtime() + INTERVALO_MS

            // Tenta alarme exato apenas quando a permissão está de fato concedida.
            // Sem essa verificação o setExactAndAllowWhileIdle lança SecurityException
            // no Android 12+ e derruba o processo inteiro na inicialização.
            val podeExato = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }

            runCatching {
                if (podeExato) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, proximo, pending
                    )
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, proximo, pending
                        )
                    } else {
                        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, proximo, pending)
                    }
                }
            }
        }

        fun cancelarVigilancia(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                ALARME_REQUEST_CODE,
                Intent(context, KeepAliveReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            pending?.let { am.cancel(it) }
        }
    }
}
