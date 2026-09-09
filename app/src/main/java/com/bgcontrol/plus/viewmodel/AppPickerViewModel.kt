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
import com.bgcontrol.plus.util.PackageUtils
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

    /** Pacotes que precisam aparecer na lista mesmo fora do filtro atual. */
    private var garantirVisiveis: Set<String> = emptySet()

    fun load(
        excludeRestricted: Boolean,
        excludeBlocked: Boolean,
        alwaysVisible: Set<String> = emptySet()
    ) {
        excluirRestritos = excludeRestricted
        excluirBloqueados = excludeBlocked
        garantirVisiveis = alwaysVisible
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
                allApps = comExtras(apps),
                alreadyUsed = used,
                isLoading = false
            )
        }
    }

    /**
     * Acrescenta à lista os pacotes que precisam estar visíveis mas ficaram de
     * fora do filtro — tipicamente apps do sistema já escolhidos, com o botão
     * "apps do sistema" desligado.
     *
     * Sem isto, ao editar uma lista existente esses itens não apareciam, e o
     * que não aparece não pode ser desmarcado: o usuário desmarcava o que via,
     * salvava, e o item invisível continuava lá.
     */
    private fun comExtras(apps: List<InstalledApp>): List<InstalledApp> {
        if (garantirVisiveis.isEmpty()) return apps
        val contexto = getApplication<Application>()
        val conhecidos = apps.mapTo(mutableSetOf()) { it.packageName }
        val faltando = garantirVisiveis.filterNot { it in conhecidos }
        if (faltando.isEmpty()) return apps

        val extras = faltando.map { pacote ->
            InstalledApp(
                packageName = pacote,
                appName = PackageUtils.getAppLabel(contexto, pacote) ?: pacote,
                isSystem = PackageUtils.isSystemPackage(contexto, pacote)
            )
        }
        return (apps + extras).sortedBy { it.appName.lowercase() }
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

    /**
     * Aplicativos marcados no momento.
     *
     * O filtro por [AppPickerUiState.allApps] sozinho perdia seleções: a lista
     * carregada é só a dos apps do usuário, então qualquer pacote de sistema já
     * pertencente ao grupo sumia na hora de salvar — e o grupo voltava com
     * menos aplicativos do que tinha, ou com nenhum. Os que não estão na lista
     * visível são remontados pelo PackageManager em vez de descartados.
     */
    fun selectedApps(): List<InstalledApp> {
        val estado = _uiState.value
        val porPacote = estado.allApps.associateBy { it.packageName }
        val contexto = getApplication<Application>()
        return estado.selected.map { pacote ->
            porPacote[pacote] ?: InstalledApp(
                packageName = pacote,
                appName = PackageUtils.getAppLabel(contexto, pacote) ?: pacote
            )
        }
    }

    /** Só os nomes de pacote marcados, para listas que guardam apenas isso. */
    fun selectedPackages(): Set<String> = _uiState.value.selected

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
