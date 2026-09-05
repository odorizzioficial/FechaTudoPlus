package com.bgcontrol.plus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgcontrol.plus.data.entities.RestrictedAppEntity
import com.bgcontrol.plus.data.repository.AppListRepository
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.monitor.AppMonitor
import com.bgcontrol.plus.util.DefaultProtectedApps
import com.bgcontrol.plus.preferences.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RestrictedUiState(
    val apps: List<RestrictedAppEntity> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    val memoryByPackage: Map<String, Long> = emptyMap(),
    val message: UiMessage? = null
)

/** Mensagem para a interface, resolvida em string localizada na tela. */
sealed interface UiMessage {
    data class Added(val count: Int) : UiMessage
    data class Removed(val appName: String) : UiMessage
    data class Blocked(val appName: String) : UiMessage
}

class RestrictedViewModel(
    application: Application,
    private val repository: AppListRepository,
    private val monitor: AppMonitor,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RestrictedUiState())
    val uiState: StateFlow<RestrictedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.blockedApps.collect { blocked ->
                _uiState.value = _uiState.value.copy(
                    blockedPackages = blocked.map { it.packageName }.toSet()
                )
            }
        }
        viewModelScope.launch {
            repository.restrictedApps.collect { apps ->
                _uiState.value = _uiState.value.copy(apps = apps)
                refreshMemory(apps.map { it.packageName })
            }
        }
    }

    private suspend fun refreshMemory(packages: List<String>) {
        val map = mutableMapOf<String, Long>()
        packages.forEach { pkg ->
            monitor.memoryOf(pkg)?.let { map[pkg] = it }
        }
        _uiState.value = _uiState.value.copy(memoryByPackage = map)
    }

    /**
     * Recoloca os aplicativos padrão do aparelho que tenham sido removidos.
     * Só afeta esta lista: nada é encerrado nem alterado em outras abas.
     */
    fun restoreDefaults() {
        viewModelScope.launch {
            val padroes = DefaultProtectedApps.detect(getApplication())
            val atuais = repository.restrictedPackages()
            val faltando = padroes.filterNot { it.packageName in atuais }
            if (faltando.isNotEmpty()) repository.addRestricted(faltando)
            _uiState.value = _uiState.value.copy(message = UiMessage.Added(faltando.size))
        }
    }

    fun addApps(apps: List<InstalledApp>) {
        viewModelScope.launch {
            repository.addRestricted(apps)
            _uiState.value = _uiState.value.copy(message = UiMessage.Added(apps.size))
        }
    }

    /**
     * Atalho da aba Restritos: manda o app também para os Bloqueados.
     * As duas listas convivem — ele fica protegido do "Fechar Tudo" e ainda
     * assim é encerrado quando sai do primeiro plano.
     */
    fun toggleBlocked(app: RestrictedAppEntity, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked) {
                repository.addBlocked(
                    InstalledApp(packageName = app.packageName, appName = app.appName)
                )
                monitor.setBackgroundRestricted(app.packageName, true)
                _uiState.value = _uiState.value.copy(
                    message = UiMessage.Blocked(app.appName)
                )
            } else {
                monitor.setBackgroundRestricted(app.packageName, false)
                repository.removeBlocked(app.packageName)
            }
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setRestrictedEnabled(packageName, enabled) }
    }

    fun remove(app: RestrictedAppEntity) {
        viewModelScope.launch {
            repository.removeRestricted(app.packageName)
            _uiState.value = _uiState.value.copy(message = UiMessage.Removed(app.appName))
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
