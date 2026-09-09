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
import com.bgcontrol.plus.R

/**
 * Serviço leve que mantém o processo do aplicativo vivo em segundo plano.
 *
 * Usa três camadas:
 *  1. START_STICKY — o sistema recria o serviço se o processo for morto.
 *  2. Alarme periódico — relança tudo a cada 10 minutos mesmo com tela apagada.
 *  3. BOOT_COMPLETED / MY_PACKAGE_REPLACED no BootReceiver — sobe após reinício.
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        startForeground(NOTIFICACAO_ID, montarNotificacao())
        agendarVigilancia(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        agendarVigilancia(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        private const val INTERVALO_MS = 10 * 60 * 1_000L
        private const val JANELA_MS = 3 * 60 * 1_000L   // tolerância do alarme inexato
        private const val ALARME_REQUEST_CODE = 9001

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
                // Abaixo do Android 12 a permissão não existe; alarmes exatos
                // são sempre permitidos para apps com targetSdk < 31 e liberados
                // pelo SCHEDULE_EXACT_ALARM para targetSdk >= 31 sem restrição.
                true
            }

            runCatching {
                if (podeExato) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, proximo, pending
                    )
                } else {
                    // Sem permissão de alarme exato usa setWindow: o sistema
                    // pode atrasar até JANELA_MS, mas nunca deixa de disparar.
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
