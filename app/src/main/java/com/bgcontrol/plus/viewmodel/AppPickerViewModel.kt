package com.bgcontrol.plus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppPickerUiState(
    val query: String = "",
    val includeSystem: Boolean = false,
    val allApps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val alreadyUsed: Set<String> = emptySet(),
    val isLoading: Boolean = true
) {
    val visibleApps: List<InstalledApp>
        get() = if (query.isBlank()) allApps else allApps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
}

/** Seleção de aplicativos instalados pelo usuário, com busca e múltipla escolha. */
class AppPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as BgControlApp).container

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    private var excluirRestritos = false
    private var excluirBloqueados = false

    fun load(excludeRestricted: Boolean, excludeBlocked: Boolean) {
        excluirRestritos = excludeRestricted
        excluirBloqueados = excludeBlocked
        recarregar(_uiState.value.includeSystem)
    }

    /** Alterna entre "só os meus apps" e "todos, incluindo os do sistema". */
    fun toggleSystemApps() {
        recarregar(!_uiState.value.includeSystem)
    }

    private fun recarregar(includeSystem: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, includeSystem = includeSystem)
            val apps = withContext(Dispatchers.IO) {
                container.appMonitor.allApps(includeSystem)
            }
            val used = buildSet {
                if (excluirRestritos) addAll(container.repository.restrictedPackages())
                if (excluirBloqueados) addAll(container.repository.blockedPackages())
            }
            _uiState.value = _uiState.value.copy(
                allApps = apps,
                alreadyUsed = used,
                isLoading = false
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun toggle(packageName: String) {
        val current = _uiState.value.selected
        _uiState.value = _uiState.value.copy(
            selected = if (packageName in current) current - packageName else current + packageName
        )
    }

    fun selectedApps(): List<InstalledApp> =
        _uiState.value.allApps.filter { it.packageName in _uiState.value.selected }

    fun clear() {
        _uiState.value = _uiState.value.copy(selected = emptySet(), query = "")
    }

    /**
     * Zera busca e seleção ao abrir. O ViewModel é compartilhado entre as
     * aberturas, então sem isto um grupo novo herdaria a escolha do anterior e
     * a palavra digitada na última busca.
     */
    fun reset(preselected: List<String> = emptyList()) {
        _uiState.value = _uiState.value.copy(
            query = "",
            selected = preselected.toSet()
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppPickerViewModel(this[APPLICATION_KEY] as BgControlApp) }
        }
    }
}
