package com.bgcontrol.plus.viewmodel

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgcontrol.plus.R
import com.bgcontrol.plus.preferences.AppLanguage
import com.bgcontrol.plus.quick.QuickAccessService
import com.bgcontrol.plus.preferences.AppSettings
import com.bgcontrol.plus.preferences.SettingsRepository
import com.bgcontrol.plus.preferences.ThemeMode
import com.bgcontrol.plus.shizuku.ShizukuManager
import com.bgcontrol.plus.shizuku.ShizukuState
import com.bgcontrol.plus.qs.CloseAllTileService
import com.bgcontrol.plus.util.PackageUtils
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    val batteryOptimized: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val versionName: String = "1.0.0",
    val overlayAllowed: Boolean = false,
    /** False quando o Android está descartando as notificações do aplicativo. */
    val notificationsAllowed: Boolean = true
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val shizukuManager: ShizukuManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            shizukuManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(shizukuState = state)
            }
        }
        refreshSystemState()
    }

    /** Relê o estado real do sistema — chamado sempre que a tela volta ao foco. */
    fun refreshSystemState() {
        val context = getApplication<Application>()
        shizukuManager.refresh()
        _uiState.value = _uiState.value.copy(
            batteryOptimized = PackageUtils.isIgnoringBatteryOptimizations(context),
            hasUsageAccess = PackageUtils.hasUsageStatsPermission(context),
            versionName = PackageUtils.appVersionName(context),
            overlayAllowed = Settings.canDrawOverlays(context),
            notificationsAllowed = NotificationManagerCompat.from(context)
                .areNotificationsEnabled()
        )
    }

    fun activateShizuku() {
        when (shizukuManager.state.value) {
            ShizukuState.NOT_INSTALLED -> openShizukuDownloadPage()
            ShizukuState.NOT_RUNNING -> openShizukuApp()
            ShizukuState.PERMISSION_REQUIRED -> shizukuManager.requestPermission()
            ShizukuState.READY -> shizukuManager.refresh()
        }
    }

    /** Abre o gerenciador que estiver instalado, seja qual for o fork. */
    private fun openShizukuApp() {
        val context = getApplication<Application>()
        val pacote = shizukuManager.installedManagerPackage()
        val intent = pacote
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent) else openShizukuDownloadPage()
    }

    private fun openShizukuDownloadPage() {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Abre o fluxo oficial do Android. A confirmação é sempre do usuário —
     * nenhuma permissão é concedida silenciosamente.
     */
    fun requestBatteryExemption() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * Pede ao sistema para adicionar o bloco. A partir do Android 13 existe um
     * diálogo oficial para isso; nas versões anteriores o usuário precisa
     * arrastar o bloco manualmente, e [onManual] mostra o passo a passo.
     */
    fun requestQuickTile(onManual: () -> Unit) {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBar = context.getSystemService(StatusBarManager::class.java)
            if (statusBar != null) {
                val pedido = runCatching {
                    statusBar.requestAddTileService(
                        ComponentName(context, CloseAllTileService::class.java),
                        context.getString(R.string.close_all),
                        Icon.createWithResource(context, R.drawable.ic_tile_close_all),
                        Executor { it.run() },
                        Consumer<Int> { resultado ->
                            // O sistema devolve se o bloco foi adicionado ou já estava lá.
                            val adicionado = resultado ==
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                resultado ==
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                            if (adicionado) {
                                viewModelScope.launch {
                                    settingsRepository.setQuickTileAdded(true)
                                }
                            }
                        }
                    )
                }
                if (pedido.isSuccess) return
            }
        }
        onManual()
    }

    fun openUsageAccessSettings() {
        val context = getApplication<Application>()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setPersistentNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPersistentNotification(enabled)
            sincronizarAcoesRapidas()
        }
    }

    fun setBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBubbleEnabled(enabled)
            sincronizarAcoesRapidas()
        }
    }

    fun setBubbleExcluded(packages: Set<String>) {
        viewModelScope.launch { settingsRepository.setBubbleExcluded(packages) }
    }

    /** O serviço sobe se algum dos dois estiver ligado, e cai quando ambos saem. */
    private suspend fun sincronizarAcoesRapidas() {
        val prefs = settingsRepository.settings.first()
        QuickAccessService.sincronizar(
            getApplication(),
            prefs.persistentNotification || prefs.bubbleEnabled
        )
    }

    fun setBubbleOpacity(percent: Int) {
        viewModelScope.launch { settingsRepository.setBubbleOpacity(percent) }
    }

    fun setBubbleSize(dp: Int) {
        viewModelScope.launch { settingsRepository.setBubbleSize(dp) }
    }

    /** Reposiciona a bolha no centro da tela na próxima vez que ela for criada. */
    /**
     * Centraliza a bolha imediatamente, mesmo com o serviço já rodando.
     * Chama o serviço diretamente em vez de só gravar a preferência: a bolha
     * já visível na tela não escuta o DataStore para reposicionar sozinha —
     * só para opacidade e tamanho — então sem isso o botão parecia não fazer nada.
     */
    fun resetBubblePosition() {
        com.bgcontrol.plus.quick.QuickAccessService.resetarPosicao(getApplication())
    }

    fun setOverlayPromptShown() {
        viewModelScope.launch { settingsRepository.setOverlayPromptShown(true) }
    }

    /**
     * Abre a tela de notificações do próprio aplicativo.
     *
     * Vale para os dois casos que impedem a notificação fixa de aparecer: a
     * permissão negada no Android 13+ e o canal desligado à mão. O diálogo de
     * permissão só pode ser mostrado uma vez pelo sistema; daí em diante este é
     * o único caminho.
     */
    fun openNotificationSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Abre a tela oficial do Android para permitir a sobreposição. */
    fun requestOverlayPermission() {
        val context = getApplication<Application>()
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun setGlassIntensity(percent: Int) {
        viewModelScope.launch { settingsRepository.setGlassIntensity(percent) }
    }

    fun setGlassEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGlassEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setOnboardingDone() {
        viewModelScope.launch { settingsRepository.setOnboardingDone(true) }
    }

    fun setBatteryDialogShown() {
        viewModelScope.launch { settingsRepository.setBatteryDialogShown(true) }
    }
}
