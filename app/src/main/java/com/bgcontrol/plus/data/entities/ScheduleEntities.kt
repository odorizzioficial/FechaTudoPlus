package com.bgcontrol.plus.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** O que fazer com os aplicativos do grupo quando a hora chegar. */
enum class ScheduleMode { FORCE_STOP, BLOCK, RESTRICT }

/**
 * Grupo agendado: um horário e um modo, com os aplicativos escolhidos.
 *
 * [daysOfWeek] é uma máscara de bits — bit 0 é domingo, bit 6 é sábado.
 * Zero significa todos os dias.
 *
 * Com [repeatEnabled] desligado o grupo roda uma única vez, na data guardada em
 * [runAtDate], e se desativa sozinho depois.
 */
@Entity(tableName = "schedule_groups")
data class ScheduleGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String = ScheduleMode.FORCE_STOP.name,
    val hour: Int = 3,
    val minute: Int = 0,
    val second: Int = 0,
    val daysOfWeek: Int = 0,
    @ColumnInfo(defaultValue = "1") val repeatEnabled: Boolean = true,
    val runAtDate: Long? = null,
    /** Repetição por intervalo, em segundos. Nulo = usa o horário fixo. */
    val intervalSeconds: Int? = null,
    val isEnabled: Boolean = true,
    val lastRunAt: Long? = null
) {
    /** Dias marcados, de 1 (domingo) a 7 (sábado), no padrão do Calendar. */
    val selectedDays: List<Int>
        get() = (0..6).filter { daysOfWeek shr it and 1 == 1 }.map { it + 1 }

    val scheduleMode: ScheduleMode
        get() = runCatching { ScheduleMode.valueOf(mode) }.getOrDefault(ScheduleMode.FORCE_STOP)
}

/** Aplicativo que pertence a um grupo agendado. */
@Entity(tableName = "schedule_apps", primaryKeys = ["groupId", "packageName"])
data class ScheduleAppEntity(
    val groupId: Long,
    val packageName: String,
    val appName: String
)
