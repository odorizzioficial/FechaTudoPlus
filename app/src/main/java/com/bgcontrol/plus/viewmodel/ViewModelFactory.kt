package com.bgcontrol.plus.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.bgcontrol.plus.BgControlApp

private fun CreationExtras.app(): BgControlApp =
    this[APPLICATION_KEY] as BgControlApp

object AppViewModelFactories {

    val running: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            RunningViewModel(
                application = app,
                monitor = app.container.appMonitor,
                repository = app.container.repository,
                shizukuManager = app.container.shizukuManager
            )
        }
    }

    val restricted: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            RestrictedViewModel(
                application = app,
                repository = app.container.repository,
                monitor = app.container.appMonitor,
                settingsRepository = app.container.settingsRepository
            )
        }
    }

    val blocked: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            BlockedViewModel(
                application = app,
                repository = app.container.repository,
                monitor = app.container.appMonitor,
                settingsRepository = app.container.settingsRepository
            )
        }
    }

    val schedule: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            ScheduleViewModel(
                application = app,
                repository = app.container.scheduleRepository
            )
        }
    }

    val settings: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            SettingsViewModel(
                application = app,
                settingsRepository = app.container.settingsRepository,
                shizukuManager = app.container.shizukuManager
            )
        }
    }
}
