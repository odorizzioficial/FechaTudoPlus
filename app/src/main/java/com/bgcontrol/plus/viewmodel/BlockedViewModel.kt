package com.bgcontrol.plus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgcontrol.plus.data.entities.BlockedAppEntity
import com.bgcontrol.plus.data.repository.AppListRepository
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.monitor.AppMonitor
import com.bgcontrol.plus.monitor.BlockedAppWatcherService
import com.bgcontrol.plus.preferences.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BlockedUiState(
    val apps: List<BlockedAppEntity> = emptyList(),
    val totalReclaimedBytes: Long = 0L,
    val message: UiMessage? = null
)

class BlockedViewModel(
    application: Application,
    private val repository: AppListRepository,
    private val monitor: AppMonitor,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BlockedUiState())
    val uiState: StateFlow<BlockedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.blockedApps,
                repository.totalReclaimedBytes
            ) { apps, reclaimed -> apps to reclaimed }
                .collect { (apps, reclaimed) ->
                    _uiState.value = _uiState.value.copy(
                        apps = apps,
                        totalReclaimedBytes = reclaimed
                    )
                    syncWatcher(apps)
                }
        }
    }

    private suspend fun syncWatcher(apps: List<BlockedAppEntity>) {
        val enabled = settingsRepository.settings.first().blockerServiceEnabled
        val context = getApplication<Application>()
        if (enabled && apps.any { it.isEnabled }) {
            BlockedAppWatcherService.start(context)
        } else {
            BlockedAppWatcherService.stop(context)
        }
    }

    fun addApps(apps: List<InstalledApp>) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.addBlocked(apps)
                // A lista do monitor é atualizada aqui mesmo: o Flow do banco
                // chega um instante depois, e nesse intervalo o encerramento
                // ainda liberaria o pacote recém-bloqueado.
                monitor.pacotesBloqueados = monitor.pacotesBloqueados + apps.map { it.packageName }
                // Bloquear não é só encerrar: o app também deixa de poder se religar.
                apps.forEach { monitor.aplicarBloqueio(it.packageName) }
            }
            _uiState.value = _uiState.value.copy(message = UiMessage.Added(apps.size))
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.setBlockedEnabled(packageName, enabled)
                if (enabled) {
                    monitor.pacotesBloqueados = monitor.pacotesBloqueados + packageName
                    monitor.aplicarBloqueio(packageName)
                } else {
                    monitor.pacotesBloqueados = monitor.pacotesBloqueados - packageName
                    monitor.removerBloqueio(packageName)
                }
            }
        }
    }

    /** Atraso, em segundos, antes de encerrar este app quando ele sair de
     *  primeiro plano. Nulo remove o atraso e volta a encerrar na hora.
     *
     *  BUG CORRIGIDO: essa gravação rodava presa ao ciclo de vida da tela
     *  (viewModelScope). Se o usuário salvasse e fechasse o app rapidamente
     *  em seguida, o Android podia encerrar essa tarefa antes dela realmente
     *  escrever no banco — o valor então nunca era salvo de verdade, e ao
     *  reabrir o app aparecia o valor antigo. NonCancellable garante que essa
     *  escrita específica sempre termine, mesmo que a tela feche logo depois. */
    fun setDelay(packageName: String, seconds: Int?) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.setBlockedDelay(packageName, seconds)
            }
        }
    }

    fun unblock(app: BlockedAppEntity) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                // Desbloquear devolve o app ao estado normal do Android: sai da
                // suspensão, recupera as permissões de segundo plano e volta ao
                // balde de uso normal.
                repository.removeBlocked(app.packageName)
                monitor.pacotesBloqueados = monitor.pacotesBloqueados - app.packageName
                monitor.removerBloqueio(app.packageName)
            }
            _uiState.value = _uiState.value.copy(message = UiMessage.Removed(app.appName))
        }
    }

    /** Tentativa manual imediata de encerrar um app bloqueado. */
    fun stopNow(app: BlockedAppEntity) {
        viewModelScope.launch {
            val reclaimed = monitor.memoryOf(app.packageName)
            val stopped = monitor.stopApp(app.packageName)
            repository.registerBlockedAction(app.packageName, stopped, reclaimed)
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
