package com.bgcontrol.plus.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.model.MonitorSource
import com.bgcontrol.plus.model.RunningAppInfo
import com.bgcontrol.plus.ui.components.ActionPill
import com.bgcontrol.plus.ui.components.AppIconWithBadge
import com.bgcontrol.plus.ui.components.AppSecondaryLine
import com.bgcontrol.plus.ui.components.AppTab
import com.bgcontrol.plus.ui.components.accent
import com.bgcontrol.plus.ui.components.GlassActionButton
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.MetricBar
import com.bgcontrol.plus.ui.components.ScreenHeader
import com.bgcontrol.plus.ui.components.bottomBarContentPadding
import com.bgcontrol.plus.ui.theme.Glass
import com.bgcontrol.plus.ui.theme.glassSurface
import com.bgcontrol.plus.util.Formatters
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.RunningViewModel

@Composable
fun RunningScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit = {},
    viewModel: RunningViewModel = viewModel(factory = AppViewModelFactories.running)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.onScreenVisible() }

    // Voltar de outro app relê a lista na hora, sem esperar o próximo ciclo.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.lastStopFailed) {
        val nome = state.lastStopFailed ?: return@LaunchedEffect
        onMessage(context.getString(R.string.stop_failed, nome))
        viewModel.consumeLastStopFailed()
    }

    LaunchedEffect(state.lastBlocked) {
        val nome = state.lastBlocked ?: return@LaunchedEffect
        onMessage(context.getString(R.string.app_blocked_added, nome))
        viewModel.consumeLastBlocked()
    }

    LaunchedEffect(state.lastRestricted) {
        val nome = state.lastRestricted ?: return@LaunchedEffect
        onMessage(context.getString(R.string.app_restricted_added, nome))
        viewModel.consumeLastRestricted()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomBarContentPadding().calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScreenHeader(
                        title = stringResource(R.string.running_title),
                        subtitle = stringResource(R.string.running_subtitle, state.apps.size),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    )
                    ActionPill(
                        text = stringResource(R.string.apps_label),
                        icon = Icons.Rounded.Refresh,
                        color = AppTab.RUNNING.accent(),
                        contentDescription = stringResource(R.string.refresh),
                        onClick = viewModel::refresh
                    )
                }
            }

            if (state.source != MonitorSource.SHIZUKU) {
                item {
                    LimitationCard(
                        text = when (state.source) {
                            MonitorSource.USAGE_STATS_LIMITED ->
                                stringResource(R.string.limitation_no_shizuku)
                            else -> stringResource(R.string.limitation_no_access)
                        },
                        actionLabel = stringResource(R.string.open_settings),
                        onAction = onOpenSettings
                    )
                }
            }

            if (state.apps.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        title = stringResource(R.string.running_empty_title),
                        description = stringResource(R.string.running_empty_description)
                    )
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                RunningAppCard(
                    modifier = Modifier.animateItem(),
                    app = app,
                    memoryLabel = Formatters.memory(context, app.memoryBytes),
                    onStop = { viewModel.stopSingle(app.packageName) },
                    onBlock = { viewModel.blockApp(app) },
                    onRestrict = { viewModel.restrictApp(app) }
                )
            }
        }

        if (state.apps.isNotEmpty()) {
            GlassActionButton(
                text = stringResource(R.string.close_all),
                icon = Icons.Rounded.Bolt,
                tint = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.error,
                enabled = !state.isClosingAll,
                onClick = viewModel::closeAll,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBarContentPadding().calculateBottomPadding())
            )
        }
    }
}

@Composable
private fun RunningAppCard(
    modifier: Modifier = Modifier,
    app: RunningAppInfo,
    memoryLabel: String,
    onStop: () -> Unit,
    onBlock: () -> Unit,
    onRestrict: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val alto = app.memoryBytes != null && app.memoryBytes > 700L * 1024 * 1024
    // A barra reflete a memória medida em relação a 1 GB. Sem medição, fica vazia.
    val progress = app.memoryBytes?.let { (it / (1024f * 1024f * 1024f)).coerceIn(0f, 1f) } ?: 0f
    val accent = when {
        app.memoryBytes == null -> MaterialTheme.colorScheme.outlineVariant
        alto -> MaterialTheme.colorScheme.error
        app.isForeground -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    GlassCard(modifier = modifier) {
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
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stop_app)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = AppTab.RUNNING.accent()
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onStop()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.block_app)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = null,
                                    tint = AppTab.BLOCKED.accent()
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onBlock()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restrict_app)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = AppTab.RESTRICTED.accent()
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRestrict()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(R.string.memory),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = memoryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (alto) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            MetricBar(progress = progress, color = accent)
        }
    }
}
