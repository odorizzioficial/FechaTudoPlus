package com.bgcontrol.plus

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bgcontrol.plus.preferences.AppLanguage
import com.bgcontrol.plus.preferences.AppSettings
import com.bgcontrol.plus.ui.components.AppTab
import com.bgcontrol.plus.ui.components.BottomNavBar
import com.bgcontrol.plus.ui.components.BottomNavContent
import com.bgcontrol.plus.ui.components.LanguageSelector
import com.bgcontrol.plus.ui.screens.BlockedScreen
import com.bgcontrol.plus.ui.screens.OnboardingDialog
import com.bgcontrol.plus.ui.screens.RestrictedScreen
import com.bgcontrol.plus.ui.screens.RunningScreen
import com.bgcontrol.plus.ui.screens.ScheduleScreen
import com.bgcontrol.plus.ui.screens.SettingsScreen
import com.bgcontrol.plus.ui.theme.glassContainerColor
import com.bgcontrol.plus.ui.theme.BgControlTheme
import com.bgcontrol.plus.ui.theme.GlassBackground
import com.bgcontrol.plus.ui.theme.ProvideAppLocale
import com.example.liquidglass.LiquidGlassView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val settingsState = MutableStateFlow(AppSettings())
    private val settingsFlow: StateFlow<AppSettings> = settingsState.asStateFlow()

    /** Aba atual, compartilhada entre o conteúdo e a barra dentro do vidro. */
    private val selectedTab: MutableState<AppTab> = mutableStateOf(AppTab.RUNNING)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val container = (application as BgControlApp).container

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settingsRepository.settings.collect { settingsState.value = it }
            }
        }

        val conteudo = findViewById<ComposeView>(R.id.content_compose)
        val vidro = findViewById<LiquidGlassView>(R.id.glass_nav)
        val barra = findViewById<ComposeView>(R.id.nav_compose)

        // Sem isto o fundo é capturado uma única vez e o vidro parece congelado.
        vidro.enableDynamicBackground = true

        // A barra de vidro acompanha o interruptor da tela Aparência.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsFlow.collect { vidro.isVisible = it.glassEnabled }
            }
        }

        // A barra flutuante sobe conforme a barra de navegação do sistema.
        ViewCompat.setOnApplyWindowInsetsListener(vidro) { view, insets ->
            val barrasSistema = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = barrasSistema.bottom +
                    (16 * view.resources.displayMetrics.density).toInt()
            }
            insets
        }

        conteudo.setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            BgControlTheme(
                themeMode = settings.themeMode,
                glassEnabled = settings.glassEnabled,
                glassIntensity = settings.glassIntensity
            ) {
                ProvideAppLocale(settings.language) {
                  GlassBackground(modifier = Modifier.fillMaxSize()) {
                    AppContent(
                        selectedTab = selectedTab,
                        language = settings.language,
                        onLanguageChange = ::trocarIdioma,
                        onOnboardingDone = ::concluirOnboarding,
                        // Sem vidro líquido, a barra é desenhada aqui dentro.
                        drawNavBar = !settings.glassEnabled,
                        showOnboarding = !settings.onboardingDone
                    )
                  }
                }
            }
        }

        barra.setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            BgControlTheme(
                themeMode = settings.themeMode,
                glassEnabled = settings.glassEnabled,
                glassIntensity = settings.glassIntensity
            ) {
                ProvideAppLocale(settings.language) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        BottomNavContent(
                            selected = selectedTab.value,
                            onSelect = { selectedTab.value = it }
                        )
                    }
                }
            }
        }
    }

    /**
     * Só grava a preferência. Quem aplica o idioma é o ProvideAppLocale, que
     * recompõe na hora — sem reiniciar a Activity e sem piscar a tela.
     */
    private fun concluirOnboarding() {
        lifecycleScope.launch {
            (application as BgControlApp).container.settingsRepository.setOnboardingDone(true)
        }
    }

    private fun trocarIdioma(language: AppLanguage) {
        lifecycleScope.launch {
            (application as BgControlApp).container.settingsRepository.setLanguage(language)
        }
    }

    override fun onResume() {
        super.onResume()
        (application as BgControlApp).container.shizukuManager.refresh()
    }
}

@Composable
private fun AppContent(
    selectedTab: MutableState<AppTab>,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onOnboardingDone: () -> Unit,
    drawNavBar: Boolean,
    showOnboarding: Boolean
) {
    var onboardingVisivel by remember(showOnboarding) { mutableStateOf(showOnboarding) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { mensagem ->
        scope.launch { snackbarHostState.showSnackbar(mensagem) }
    }

    var languageDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = selectedTab.value.ordinal,
        pageCount = { AppTab.entries.size }
    )

    // O pager só assume a aba quando o gesto termina (settledPage). Usar
    // currentPage aqui fazia a animação programada brigar com o dedo do usuário
    // e a tela parava no meio do caminho entre duas abas.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { pagina ->
            selectedTab.value = AppTab.entries[pagina]
        }
    }
    LaunchedEffect(selectedTab.value) {
        val destino = selectedTab.value.ordinal
        if (pagerState.currentPage != destino && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(destino)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Faixa superior, na altura do relógio do sistema.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                LanguageSelector(
                    current = language,
                    onChange = onLanguageChange,
                    onOpenList = { languageDialog = true }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 8.dp
            ) { page ->
                when (AppTab.entries[page]) {
                    AppTab.RUNNING -> RunningScreen(
                        onOpenSettings = { selectedTab.value = AppTab.SETTINGS },
                        onMessage = showMessage
                    )
                    AppTab.RESTRICTED -> RestrictedScreen(onMessage = showMessage)
                    AppTab.BLOCKED -> BlockedScreen(onMessage = showMessage)
                    AppTab.SCHEDULE -> ScheduleScreen()
                    AppTab.SETTINGS -> SettingsScreen()
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.Bottom
        ) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (drawNavBar) {
                BottomNavBar(
                    selected = selectedTab.value,
                    onSelect = { selectedTab.value = it }
                )
            } else {
                Box(modifier = Modifier.padding(bottom = 96.dp))
            }
        }
    }

    if (onboardingVisivel) {
        OnboardingDialog(
            onFinish = {
                onboardingVisivel = false
                onOnboardingDone()
            }
        )
    }

    if (languageDialog) {
        LanguageDialog(
            current = language,
            onSelect = {
                onLanguageChange(it)
                languageDialog = false
            },
            onDismiss = { languageDialog = false }
        )
    }
}

/** Lista completa de idiomas, aberta ao tocar no seletor do topo. */
@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.language_title)) },
        containerColor = glassContainerColor(),
        text = {
            LazyColumn {
                items(AppLanguage.entries) { idioma ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(idioma) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = idioma == current,
                            onClick = { onSelect(idioma) }
                        )
                        Text(
                            text = "${idioma.flag} ${idioma.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    )
}

