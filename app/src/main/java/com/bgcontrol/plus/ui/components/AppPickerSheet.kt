package com.bgcontrol.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.ui.theme.glassContainerColor
import com.bgcontrol.plus.viewmodel.AppPickerViewModel

/**
 * Folha de seleção de aplicativos: lista os apps instalados pelo usuário,
 * com busca e seleção múltipla. Apps já presentes na lista aparecem desabilitados.
 *
 * Com [manageMode] a folha deixa de ser só "adicionar" e passa a editar uma
 * lista existente: os itens já escolhidos chegam marcados em [preSelected],
 * desmarcar realmente remove e o botão continua ativo com nenhum marcado, para
 * que dê para esvaziar a lista. Sem isso, desmarcar não fazia nada — o botão
 * devolvia apenas o que estava marcado e a tela somava ao conjunto antigo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    title: String,
    excludeRestricted: Boolean,
    excludeBlocked: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<InstalledApp>) -> Unit,
    preSelected: Set<String> = emptySet(),
    manageMode: Boolean = false,
    onConfirmPackages: ((Set<String>) -> Unit)? = null
) {
    val viewModel: AppPickerViewModel = viewModel(factory = AppPickerViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var avisoSistema by remember { mutableStateOf(false) }

    if (avisoSistema) {
        AlertDialog(
            onDismissRequest = { avisoSistema = false },
            containerColor = glassContainerColor(),
            title = { Text(stringResource(R.string.system_apps_warning_title)) },
            text = { Text(stringResource(R.string.system_apps_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.toggleSystemApps()
                        avisoSistema = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.understood),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { avisoSistema = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.reset(preSelected.toList())
        // Um app já escolhido pode ser do sistema, e a lista curta não o traria.
        // Sem isto ele nem apareceria para ser desmarcado.
        viewModel.load(
            excludeRestricted = excludeRestricted,
            excludeBlocked = excludeBlocked,
            alwaysVisible = if (manageMode) preSelected else emptySet()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassContainerColor(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Quantos ainda podem ser escolhidos: cai a cada seleção.
                    Text(
                        text = stringResource(
                            R.string.available_apps,
                            (state.visibleApps.count { it.packageName !in state.alreadyUsed } -
                                state.selected.size).coerceAtLeast(0)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Apps do sistema só aparecem quando o usuário pede.
                ActionPill(
                    text = stringResource(R.string.system_apps_button),
                    icon = Icons.Rounded.Android,
                    color = if (state.includeSystem) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    contentDescription = stringResource(
                        if (state.includeSystem) {
                            R.string.hide_system_apps
                        } else {
                            R.string.show_system_apps
                        }
                    ),
                    onClick = {
                        if (state.includeSystem) {
                            viewModel.toggleSystemApps()
                        } else {
                            avisoSistema = true
                        }
                    }
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.small
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        val disabled = app.packageName in state.alreadyUsed
                        AppPickerRow(
                            app = app,
                            checked = app.packageName in state.selected,
                            enabled = !disabled,
                            onToggle = { viewModel.toggle(app.packageName) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (manageMode) {
                        onConfirmPackages?.invoke(viewModel.selectedPackages())
                    } else {
                        onConfirm(viewModel.selectedApps())
                    }
                    viewModel.clear()
                },
                // Ao gerenciar, salvar com a lista vazia é uma escolha válida.
                enabled = manageMode || state.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when {
                        manageMode -> stringResource(R.string.save_selection, state.selected.size)
                        state.selected.isEmpty() -> stringResource(R.string.select_apps)
                        else -> stringResource(R.string.add_selected, state.selected.size)
                    }
                )
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledApp,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                MaterialTheme.shapes.small
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconWithBadge(packageName = app.packageName, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (enabled) {
                AppSecondaryLine(packageName = app.packageName)
            } else {
                Text(
                    text = stringResource(R.string.already_in_list),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}
