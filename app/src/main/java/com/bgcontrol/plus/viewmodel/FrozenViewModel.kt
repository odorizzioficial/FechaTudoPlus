package com.bgcontrol.plus.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.data.entities.FrozenAppEntity
import com.bgcontrol.plus.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FrozenUiState(
    val apps: List<FrozenAppEntity> = emptyList(),
    val isLoading: Boolean = true,
    val message: FrozenMessage? = null
)

sealed class FrozenMessage {
    data class Frozen(val name: String) : FrozenMessage()
    data class Unfrozen(val name: String) : FrozenMessage()
    object NoShizuku : FrozenMessage()
}

class FrozenViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BgControlApp
    private val dao = app.container.db.frozenAppDao()
    private val monitor = app.container.appMonitor

    private val _uiState = MutableStateFlow(FrozenUiState())
    val uiState: StateFlow<FrozenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.all().collect { lista ->
                _uiState.value = _uiState.value.copy(apps = lista, isLoading = false)
            }
        }
    }

    fun freeze(apps: List<InstalledApp>) {
        viewModelScope.launch {
            if (!monitor.shizukuReady()) {
                _uiState.value = _uiState.value.copy(message = FrozenMessage.NoShizuku)
                return@launch
            }
            apps.forEach { app ->
                val cmd = buildFreezeCmd(app.packageName)
                monitor.execPrivileged(cmd)
                dao.insert(FrozenAppEntity(packageName = app.packageName, appName = app.appName))
            }
            _uiState.value = _uiState.value.copy(
                message = if (apps.size == 1) FrozenMessage.Frozen(apps.first().appName) else null
            )
        }
    }

    /**
     * Descongela o app.
     *
     * Uma tentativa anterior fixava o atalho de volta na tela inicial via
     * requestPinShortcut, mas todo atalho fixado por um app carrega um selo
     * visível do app que fez o pedido — nunca fica limpo com o ícone só do
     * app de destino. Como isso piorava a experiência em vez de ajudar, foi
     * removido. Sem uma forma de restaurar a posição exata (ver nota em
     * [buildFreezeCmd]), o app volta à gaveta e o usuário decide se recoloca
     * na tela inicial manualmente.
     */
    fun unfreeze(entity: FrozenAppEntity) {
        viewModelScope.launch {
            if (!monitor.shizukuReady()) {
                _uiState.value = _uiState.value.copy(message = FrozenMessage.NoShizuku)
                return@launch
            }
            // --user 0 espelha o comando de congelar (disable-user --user 0).
            monitor.execPrivileged("pm enable --user 0 ${entity.packageName}")
            dao.delete(entity.packageName)
            _uiState.value = _uiState.value.copy(message = FrozenMessage.Unfrozen(entity.appName))
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    private fun buildFreezeCmd(pkg: String): String {
        // disable-user é reversível: o app some da gaveta mas pode ser reabilitado.
        // Não usa disable puro porque esse apaga dados em algumas ROMs.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            "pm disable-user --user 0 $pkg"
        } else {
            "pm disable $pkg"
        }
    }
}
