package com.bgcontrol.plus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgcontrol.plus.data.repository.AppListRepository
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.model.MonitorSource
import com.bgcontrol.plus.model.RunningAppInfo
import com.bgcontrol.plus.monitor.AppMonitor
import com.bgcontrol.plus.shizuku.ShizukuManager
import com.bgcontrol.plus.util.PackageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RunningUiState(
    val apps: List<RunningAppInfo> = emptyList(),
    val source: MonitorSource = MonitorSource.UNAVAILABLE,
    val isLoading: Boolean = true,
    val isClosingAll: Boolean = false,
    val lastBlocked: String? = null,
    val lastRestricted: String? = null,
    val lastStopFailed: String? = null
)

class RunningViewModel(
    application: Application,
    private val monitor: AppMonitor,
    private val repository: AppListRepository,
    private val shizukuManager: ShizukuManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RunningUiState())
    val uiState: StateFlow<RunningUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    fun onScreenVisible() {
        refresh()
        startAutoRefresh()
    }

    fun onScreenHidden() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Atualização de fundo enquanto a aba está visível. Fora dela, nada roda. */
    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_MS)
                loadSnapshot()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadSnapshot()
        }
    }

    private suspend fun loadSnapshot() {
        shizukuManager.refresh()
        val snapshot = monitor.snapshot()
        // Aplicativos da aba Restritos não aparecem aqui: eles são protegidos
        // e nunca serão encerrados por esta tela.
        val restricted = repository.restrictedPackages()
        _uiState.value = _uiState.value.copy(
            apps = snapshot.apps.filterNot { it.packageName in restricted },
            source = snapshot.source,
            isLoading = false
        )
    }

    /**
     * Encerra tudo de uma vez. Os apps listados aqui já excluem os restritos,
     * e o launcher e o próprio aplicativo continuam fora por segurança.
     */
    fun closeAll() {
        viewModelScope.launch {
            val protectedPackages = repository.restrictedPackages()
            val launcher = PackageUtils.getDefaultLauncher(getApplication())
            val self = getApplication<Application>().packageName

            val alvos = _uiState.value.apps
                .map { it.packageName }
                .filter { it !in protectedPackages && it != launcher && it != self }

            if (alvos.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(isClosingAll = true)

            // O encerramento real dispara de uma vez, em paralelo. A remoção
            // visual acontece em cascata só para o olho acompanhar o que houve.
            val encerramento = launch { monitor.stopApps(alvos) }
            alvos.forEach { pacote ->
                _uiState.value = _uiState.value.copy(
                    apps = _uiState.value.apps.filterNot { it.packageName == pacote }
                )
                delay(CASCATA_MS)
            }
            encerramento.join()
            loadSnapshot()
            _uiState.value = _uiState.value.copy(isClosingAll = false)
        }
    }

    /**
     * Manda o aplicativo para a lista de Bloqueados e o encerra na sequência.
     * A regra de exclusividade continua valendo: se ele já estiver em Restritos,
     * o repositório recusa e nada muda.
     */
    fun blockApp(app: RunningAppInfo) {
        viewModelScope.launch {
            repository.addBlocked(
                InstalledApp(packageName = app.packageName, appName = app.appName)
            )
            monitor.setBackgroundRestricted(app.packageName, true)
            _uiState.value = _uiState.value.copy(
                apps = _uiState.value.apps.filterNot { it.packageName == app.packageName },
                lastBlocked = app.appName
            )
            monitor.stopApp(app.packageName)
            loadSnapshot()
        }
    }

    /** Atalho da aba Em execução: protege o app sem sair da tela. */
    fun restrictApp(app: RunningAppInfo) {
        viewModelScope.launch {
            repository.addRestricted(
                InstalledApp(packageName = app.packageName, appName = app.appName)
            )
            _uiState.value = _uiState.value.copy(
                apps = _uiState.value.apps.filterNot { it.packageName == app.packageName },
                lastRestricted = app.appName
            )
        }
    }

    fun consumeLastRestricted() {
        _uiState.value = _uiState.value.copy(lastRestricted = null)
    }

    fun consumeLastBlocked() {
        _uiState.value = _uiState.value.copy(lastBlocked = null)
    }

    fun stopSingle(packageName: String) {
        viewModelScope.launch {
            val nome = _uiState.value.apps
                .firstOrNull { it.packageName == packageName }?.appName ?: packageName
            _uiState.value = _uiState.value.copy(
                apps = _uiState.value.apps.filterNot { it.packageName == packageName }
            )
            val encerrado = monitor.stopApp(packageName)
            loadSnapshot()
            // Play Services e afins são reiniciados pelo sistema em seguida.
            // Em vez de fingir sucesso, a tela diz o que aconteceu de fato.
            if (!encerrado) {
                _uiState.value = _uiState.value.copy(lastStopFailed = nome)
            }
        }
    }

    fun consumeLastStopFailed() {
        _uiState.value = _uiState.value.copy(lastStopFailed = null)
    }

    private companion object {
        /** A aba só atualiza sozinha enquanto está aberta, então pode ser rápida. */
        const val AUTO_REFRESH_MS = 2_500L

        /** Intervalo entre um cartão e o seguinte na animação de "Fechar Tudo". */
        const val CASCATA_MS = 70L
    }
}
