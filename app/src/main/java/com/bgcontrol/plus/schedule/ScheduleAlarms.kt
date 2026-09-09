package com.bgcontrol.plus.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bgcontrol.plus.data.entities.ScheduleGroupEntity
import java.util.Calendar

/**
 * Registra os grupos no AlarmManager.
 *
 * A repetição vem dos campos do próprio grupo: sem dia nem mês é diário, só com
 * o dia é mensal, com dia e mês é anual. Depois de disparar, o receptor agenda
 * a próxima ocorrência — nada fica preso a um alarme repetitivo que o sistema
 * possa descartar.
 */
object ScheduleAlarms {

    const val ACTION_RUN = "com.bgcontrol.plus.RUN_SCHEDULE"
    const val EXTRA_GROUP_ID = "group_id"

    /**
     * Piso do intervalo. Abaixo disso o Android passa a adiar os alarmes por
     * conta própria e o gasto de bateria deixa de compensar.
     */
    const val MIN_INTERVALO_S = 15

    fun reschedule(context: Context, group: ScheduleGroupEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, group.id)

        if (!group.isEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val quando = nextTrigger(group)
        // Alarme exato quando permitido; caso contrário, uma janela aproximada,
        // que o Android sempre aceita. Nunca falha em silêncio.
        val exatoPermitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        runCatching {
            if (exatoPermitido) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    quando,
                    pendingIntent
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    quando,
                    5 * 60_000L,
                    pendingIntent
                )
            }
        }
    }

    fun cancel(context: Context, groupId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, groupId))
    }

    /**
     * Próximo instante em que o grupo deve rodar, sempre no futuro.
     * Sem dias marcados, roda todo dia; com dias marcados, procura o próximo
     * deles a partir de hoje.
     */
    fun nextTrigger(group: ScheduleGroupEntity, from: Long = System.currentTimeMillis()): Long {
        // Sem repetição, o horário é uma data só; se já passou, não há próxima.
        if (!group.repeatEnabled) {
            return group.runAtDate ?: from
        }

        // Repetição por intervalo: conta a partir de agora, ignorando o relógio.
        group.intervalSeconds?.let { intervalo ->
            return from + intervalo.coerceAtLeast(MIN_INTERVALO_S) * 1000L
        }

        val dias = group.selectedDays
        val alvo = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, group.hour)
            set(Calendar.MINUTE, group.minute)
            set(Calendar.SECOND, group.second)
            set(Calendar.MILLISECOND, 0)
        }

        if (dias.isEmpty()) {
            if (alvo.timeInMillis <= from) alvo.add(Calendar.DAY_OF_MONTH, 1)
            return alvo.timeInMillis
        }

        // No máximo oito passos: cobre a semana inteira e volta ao mesmo dia.
        repeat(8) {
            if (alvo.timeInMillis > from && alvo.get(Calendar.DAY_OF_WEEK) in dias) {
                return alvo.timeInMillis
            }
            alvo.add(Calendar.DAY_OF_MONTH, 1)
        }
        return alvo.timeInMillis
    }

    private fun pendingIntent(context: Context, groupId: Long): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_RUN
            putExtra(EXTRA_GROUP_ID, groupId)
        }
        return PendingIntent.getBroadcast(
            context,
            groupId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
