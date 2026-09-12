package com.bgcontrol.plus.ui.screens

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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.data.entities.FrozenAppEntity
import com.bgcontrol.plus.ui.components.AppIconTile
import com.bgcontrol.plus.ui.components.AppTab
import com.bgcontrol.plus.ui.components.AppPickerSheet
import com.bgcontrol.plus.ui.components.GlassActionButton
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.ScreenHeader
import com.bgcontrol.plus.ui.components.accent
import com.bgcontrol.plus.ui.components.bottomBarContentPadding
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.FrozenMessage
import com.bgcontrol.plus.viewmodel.FrozenViewModel

/**
 * [onMessage] sobe o aviso até o snackbar compartilhado do MainActivity, que
 * fica desenhado por cima da barra de navegação inferior. Um SnackbarHost
 * próprio desta tela ficava dentro do conteúdo da aba, atrás da barra — por
 * isso a mensagem de "app congelado" não dava para ler.
 */
@Composable
fun FrozenScreen(
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
    viewModel: FrozenViewModel = viewModel(factory = AppViewModelFactories.frozen)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cor = AppTab.FROZEN.accent()
    var seletor by remember { mutableStateOf(false) }
    var confirmarDescongelar by remember { mutableStateOf<FrozenAppEntity?>(null) }

    val msgCongelado = stringResource(R.string.frozen_done)
    val msgDescongelado = stringResource(R.string.unfrozen_done)
    val msgSemShizuku = stringResource(R.string.shizuku_required)

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        onMessage(
            when (msg) {
                is FrozenMessage.Frozen -> "$msgCongelado ${msg.name}"
                is FrozenMessage.Unfrozen -> "$msgDescongelado ${msg.name}"
                FrozenMessage.NoShizuku -> msgSemShizuku
            }
        )
        viewModel.clearMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Cabeçalho e cartão explicativo ficam fixos no topo; o restante do
        // espaço (lista ou estado vazio) ocupa o que sobra, para que o estado
        // vazio centralize de verdade na área visível — igual às outras abas.
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScreenHeader(
                    title = stringResource(R.string.tab_frozen),
                    subtitle = null,
                    contentPadding = PaddingValues(bottom = 0.dp)
                )
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AcUnit,
                                contentDescription = null,
                                tint = cor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.frozen_how_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.frozen_how_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // A barra de navegação flutua por cima do conteúdo em vez
                    // de empurrá-lo para cima, então sem descontar essa altura
                    // o centro deste espaço ficava mais para baixo do que o
                    // centro realmente visível na tela.
                    .padding(bottom = bottomBarContentPadding().calculateBottomPadding())
            ) {
                if (state.apps.isEmpty()) {
                    if (!state.isLoading) {
                        // Column com Arrangement.Center dentro do Box com peso:
                        // é o jeito mais confiável de centralizar de verdade no
                        // espaço restante, igual à aba Bloqueados.
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.frozen_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.frozen_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 8.dp, start = 32.dp, end = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp,
                            bottom = bottomBarContentPadding().calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.apps, key = { it.packageName }) { app ->
                            FrozenCard(
                                app = app,
                                cor = cor,
                                onUnfreeze = { confirmarDescongelar = app }
                            )
                        }
                    }
                }
            }
        }

        // Botão embaixo, igual ao padrão das outras abas (Restritos, Bloqueados).
        GlassActionButton(
            text = stringResource(R.string.add_app),
            icon = Icons.Rounded.Add,
            tint = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = { seletor = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = bottomBarContentPadding().calculateBottomPadding()
                )
        )
    }

    if (seletor) {
        AppPickerSheet(
            title = stringResource(R.string.frozen_pick_title),
            excludeRestricted = false,
            excludeBlocked = false,
            onDismiss = { seletor = false },
            onConfirm = { apps ->
                viewModel.freeze(apps)
                seletor = false
            }
        )
    }

    confirmarDescongelar?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmarDescongelar = null },
            title = { Text(stringResource(R.string.unfreeze_confirm_title)) },
            text = { Text(stringResource(R.string.unfreeze_confirm_body, app.appName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unfreeze(app)
                    confirmarDescongelar = null
                }) { Text(stringResource(R.string.unfreeze)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarDescongelar = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun FrozenCard(
    app: FrozenAppEntity,
    cor: androidx.compose.ui.graphics.Color,
    onUnfreeze: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconTile(
                packageName = app.packageName,
                size = 40.dp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onUnfreeze) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.unfreeze),
                    tint = cor
                )
            }
        }
    }
}
