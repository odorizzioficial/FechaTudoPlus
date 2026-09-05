package com.bgcontrol.plus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.preferences.AppLanguage
import com.bgcontrol.plus.shizuku.ShizukuState
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.theme.glassContainerColor
import com.bgcontrol.plus.ui.components.ScreenHeader
import com.bgcontrol.plus.ui.components.StatusPill
import com.bgcontrol.plus.ui.components.bottomBarContentPadding
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactories.settings)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var languageDialog by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var tileManualDialog by remember { mutableStateOf(false) }

    // Sem isto, voltar da tela do Android sobre bateria deixaria o "!" preso.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (aboutOpen) {
        AboutScreen(
            versionName = state.versionName,
            onBack = { aboutOpen = false },
            modifier = modifier
        )
        return
    }

    if (appearanceOpen) {
        AppearanceScreen(
            themeMode = state.settings.themeMode,
            glassEnabled = state.settings.glassEnabled,
            glassIntensity = state.settings.glassIntensity,
            onThemeChange = viewModel::setThemeMode,
            onGlassChange = viewModel::setGlassEnabled,
            onGlassIntensityChange = viewModel::setGlassIntensity,
            onBack = { appearanceOpen = false },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomBarContentPadding().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                contentPadding = PaddingValues(bottom = 8.dp)
            )
        }

        // Status do Shizuku — sempre o estado real do serviço.
        item {
            SettingsCard(
                icon = Icons.Rounded.Adb,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.shizuku_status),
                subtitle = when (state.shizukuState) {
                    ShizukuState.READY -> stringResource(R.string.shizuku_running)
                    ShizukuState.PERMISSION_REQUIRED ->
                        stringResource(R.string.shizuku_permission_required)
                    ShizukuState.NOT_RUNNING -> stringResource(R.string.shizuku_not_running)
                    ShizukuState.NOT_INSTALLED -> stringResource(R.string.shizuku_not_installed)
                },
                onClick = viewModel::activateShizuku,
                trailing = {
                    when (state.shizukuState) {
                        ShizukuState.READY -> StatusPill(
                            text = stringResource(R.string.status_active),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        )
                        ShizukuState.PERMISSION_REQUIRED -> StatusPill(
                            text = stringResource(R.string.status_waiting),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        else -> AttentionIcon()
                    }
                }
            )
        }

        if (state.shizukuState != ShizukuState.READY) {
            item {
                ActionButtonCard(
                    label = when (state.shizukuState) {
                        ShizukuState.NOT_INSTALLED -> stringResource(R.string.install_shizuku)
                        ShizukuState.NOT_RUNNING -> stringResource(R.string.open_shizuku)
                        else -> stringResource(R.string.activate_shizuku)
                    },
                    description = stringResource(R.string.shizuku_explanation),
                    onClick = viewModel::activateShizuku
                )
            }
        }

        // Permissão de acesso ao uso — necessária para detectar o primeiro plano.
        if (!state.hasUsageAccess) {
            item {
                ActionButtonCard(
                    label = stringResource(R.string.grant_usage_access),
                    description = stringResource(R.string.usage_access_explanation),
                    onClick = viewModel::openUsageAccessSettings
                )
            }
        }

        // Bateria: mesmo padrão visual do Shizuku — selo "Ativo" quando pronto,
        // ícone de exclamação quando o usuário ainda precisa autorizar.
        item {
            SettingsCard(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.battery_optimization),
                subtitle = if (state.batteryOptimized) {
                    stringResource(R.string.battery_configured)
                } else {
                    stringResource(R.string.battery_activate)
                },
                onClick = viewModel::requestBatteryExemption,
                trailing = {
                    if (state.batteryOptimized) {
                        StatusPill(
                            text = stringResource(R.string.status_active),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    } else {
                        AttentionIcon()
                    }
                }
            )
        }

        // Bloco das Configurações Rápidas: fecha tudo sem abrir o aplicativo.
        item {
            SettingsCard(
                icon = Icons.Rounded.Dashboard,
                title = stringResource(R.string.quick_tile_title),
                subtitle = stringResource(R.string.quick_tile_subtitle),
                // Já adicionado: o cartão fica apagado e não pede nada.
                // Se o usuário remover o bloco, ele volta a oferecer o "+".
                enabled = !state.settings.quickTileAdded,
                onClick = { viewModel.requestQuickTile { tileManualDialog = true } },
                trailing = {
                    if (state.settings.quickTileAdded) {
                        Text(
                            text = stringResource(R.string.quick_tile_added),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.quick_tile_add),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.language),
                subtitle = "${state.settings.language.flag} ${state.settings.language.displayName}",
                onClick = { languageDialog = true },
                trailing = { ChevronIcon() }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.appearance_subtitle),
                onClick = { appearanceOpen = true },
                trailing = { ChevronIcon() }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.version_format, state.versionName),
                onClick = { aboutOpen = true },
                trailing = { ChevronIcon() }
            )
        }

        item { MadeByFooter() }
    }

    if (languageDialog) {
        SelectionDialog(
            title = stringResource(R.string.language),
            options = AppLanguage.entries.map { "${it.flag} ${it.displayName}" },
            selectedIndex = AppLanguage.entries.indexOf(state.settings.language),
            onSelect = { index ->
                viewModel.setLanguage(AppLanguage.entries[index])
                languageDialog = false
            },
            onDismiss = { languageDialog = false }
        )
    }

    if (tileManualDialog) {
        AlertDialog(
            onDismissRequest = { tileManualDialog = false },
            confirmButton = {
                TextButton(onClick = { tileManualDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.quick_tile_title)) },
            text = { Text(stringResource(R.string.quick_tile_manual)) },
            containerColor = glassContainerColor()
        )
    }
}

/** Assinatura do rodapé: toque no ícone abre o canal no YouTube. */
@Composable
private fun MadeByFooter() {
    val context = LocalContext.current
    val url = stringResource(R.string.youtube_channel_url)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.made_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Image(
            painter = painterResource(R.drawable.ic_youtube),
            contentDescription = stringResource(R.string.youtube_channel),
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp)
        )
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    GlassCard(onClick = if (enabled) onClick else null) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (enabled) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun ChevronIcon() {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Exclamação: algo depende de uma autorização que o usuário ainda não deu. */
@Composable
private fun AttentionIcon() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PriorityHigh,
            contentDescription = stringResource(R.string.attention),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ActionButtonCard(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        tint = MaterialTheme.colorScheme.secondaryContainer,
        alpha = 0.42f
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(title) },
        containerColor = glassContainerColor(),
        text = {
            LazyColumn {
                itemsIndexed(options) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                        Text(
                            text = option,
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
