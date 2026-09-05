package com.bgcontrol.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MobileOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.data.entities.RestrictedAppEntity
import com.bgcontrol.plus.ui.components.ActionPill
import com.bgcontrol.plus.ui.components.AppIconWithBadge
import com.bgcontrol.plus.ui.components.AppSecondaryLine
import com.bgcontrol.plus.ui.components.AppTab
import com.bgcontrol.plus.ui.components.accent
import com.bgcontrol.plus.ui.components.AppPickerSheet
import com.bgcontrol.plus.ui.components.GlassActionButton
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.bottomBarContentPadding
import com.bgcontrol.plus.ui.components.ScreenHeader
import com.bgcontrol.plus.ui.components.StatusPill
import com.bgcontrol.plus.ui.theme.glassInner
import com.bgcontrol.plus.util.Formatters
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.RestrictedViewModel
import com.bgcontrol.plus.viewmodel.UiMessage

@Composable
fun RestrictedScreen(
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit,
    viewModel: RestrictedViewModel = viewModel(factory = AppViewModelFactories.restricted)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        onMessage(
            when (message) {
                is UiMessage.Added -> context.resources.getQuantityString(
                    R.plurals.apps_added, message.count, message.count
                )
                is UiMessage.Removed -> context.getString(R.string.app_removed, message.appName)
                is UiMessage.Blocked ->
                    context.getString(R.string.app_blocked_added, message.appName)
            }
        )
        viewModel.consumeMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                // A barra flutuante mais a altura do botão "Adicionar": sem isso
                // o último cartão fica embaixo do botão e não dá para tocar nele.
                bottom = bottomBarContentPadding().calculateBottomPadding() + 76.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    ScreenHeader(
                        title = stringResource(R.string.restricted_title),
                        subtitle = stringResource(R.string.restricted_description),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = stringResource(
                                R.string.protected_apps_count,
                                state.apps.count { it.isEnabled }
                            ),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        )
                        Spacer(Modifier.size(8.dp))
                        // Devolve os apps padrão do aparelho que tenham sido removidos.
                        ActionPill(
                            text = stringResource(R.string.restore_defaults_short),
                            icon = Icons.Rounded.Refresh,
                            color = AppTab.RESTRICTED.accent(),
                            contentDescription = stringResource(R.string.restore_defaults),
                            onClick = viewModel::restoreDefaults
                        )
                    }
                }
            }

            item {
                LimitationCard(
                    text = stringResource(R.string.defaults_protected),
                    actionLabel = null,
                    onAction = null,
                    tint = MaterialTheme.colorScheme.secondaryContainer
                )
            }

            if (state.apps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.restricted_empty_title),
                        description = stringResource(R.string.restricted_empty_description)
                    )
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                RestrictedAppCard(
                    app = app,
                    memoryLabel = state.memoryByPackage[app.packageName]
                        ?.let { Formatters.memory(context, it) }
                        ?: stringResource(R.string.not_available),
                    alsoBlocked = app.packageName in state.blockedPackages,
                    onToggle = { viewModel.setEnabled(app.packageName, it) },
                    onToggleBlocked = { viewModel.toggleBlocked(app, it) },
                    onRemove = { viewModel.remove(app) }
                )
            }
        }

        GlassActionButton(
            text = stringResource(R.string.add_app),
            icon = Icons.Rounded.Add,
            tint = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = { pickerOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = bottomBarContentPadding().calculateBottomPadding()
                )
        )
    }

    if (pickerOpen) {
        AppPickerSheet(
            title = stringResource(R.string.add_to_restricted),
            excludeRestricted = true,
            excludeBlocked = false,
            onDismiss = { pickerOpen = false },
            onConfirm = { apps ->
                viewModel.addApps(apps)
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun RestrictedAppCard(
    app: RestrictedAppEntity,
    memoryLabel: String,
    alsoBlocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleBlocked: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconWithBadge(packageName = app.packageName)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppSecondaryLine(packageName = app.packageName)
                }
                Switch(
                    checked = app.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassInner(MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (app.isEnabled) {
                        stringResource(R.string.protection_active)
                    } else {
                        stringResource(R.string.protection_paused)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = memoryLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Restrito e bloqueado ao mesmo tempo: protegido do "Fechar Tudo",
            // mas encerrado assim que sai do primeiro plano.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.MobileOff,
                    contentDescription = null,
                    tint = if (alsoBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (alsoBlocked) {
                        stringResource(R.string.already_blocked_short)
                    } else {
                        stringResource(R.string.also_block)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                Switch(
                    checked = alsoBlocked,
                    onCheckedChange = onToggleBlocked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onError,
                        checkedTrackColor = MaterialTheme.colorScheme.error,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }
    }
}
