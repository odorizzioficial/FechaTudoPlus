package com.bgcontrol.plus

import android.content.Context
import com.bgcontrol.plus.data.database.AppDatabase
import com.bgcontrol.plus.data.repository.AppListRepository
import com.bgcontrol.plus.data.repository.ScheduleRepository
import com.bgcontrol.plus.monitor.AppMonitor
import com.bgcontrol.plus.preferences.SettingsRepository
import com.bgcontrol.plus.shizuku.ShizukuManager

/** Injeção de dependências manual — mantém as telas livres de lógica. */
class AppContainer(context: Context) {

    private val database = AppDatabase.get(context)

    val repository = AppListRepository(
        restrictedDao = database.restrictedAppDao(),
        blockedDao = database.blockedAppDao()
    )

    val scheduleRepository = ScheduleRepository(database.scheduleDao())

    val settingsRepository = SettingsRepository(context)

    val shizukuManager = ShizukuManager(context)

    val appMonitor = AppMonitor(context, shizukuManager)
}
