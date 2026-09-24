package com.bgcontrol.plus.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.data.entities.BlockedAppEntity
import com.bgcontrol.plus.ui.components.AppIconWithBadge
import com.bgcontrol.plus.ui.components.AppSecondaryLine
import com.bgcontrol.plus.ui.components.AppPickerSheet
import com.bgcontrol.plus.ui.components.GlassActionButton
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.bottomBarContentPadding
import com.bgcontrol.plus.ui.components.MetricBar
import com.bgcontrol.plus.ui.components.ScreenHeader
import com.bgcontrol.plus.ui.components.StatusPill
import com.bgcontrol.plus.ui.theme.glassContainerColor
import com.bgcontrol.plus.ui.theme.glassInner
import com.bgcontrol.plus.util.Formatters
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.BlockedViewModel
import com.bgcontrol.plus.viewmodel.UiMessage

@Composable
fun BlockedScreen(
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit,
    viewModel: BlockedViewModel = viewModel(factory = AppViewModelFactories.blocked)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }
    var editandoAtrasoDe by remember { mutableStateOf<String?>(null) }

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
                ScreenHeader(
                    title = stringResource(R.string.blocked_title),
                    subtitle = stringResource(R.string.blocked_description),
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }

            item {
                // Aviso sobre a novidade do atraso: arrastar um cartão para
                // a esquerda revela a opção de configurar quantos segundos,
                // minutos ou horas esperar antes de encerrar aquele app
                // específico, em vez de encerrar na hora.
                GlassCard(tint = MaterialTheme.colorScheme.error, alpha = 0.22f) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.blocked_delay_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = stringResource(R.string.total_blocked),
                        value = state.apps.size.toString(),
                        unit = null,
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    val reclaimed = Formatters.memoryValue(state.totalReclaimedBytes)
                    StatCard(
                        label = stringResource(R.string.memory_reclaimed),
                        value = reclaimed?.first ?: stringResource(R.string.not_available),
                        unit = reclaimed?.second,
                        valueColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.apps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.blocked_empty_title),
                        description = stringResource(R.string.blocked_empty_description)
                    )
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                BlockedAppCard(
                    app = app,
                    lastAttempt = Formatters.relativeTime(context, app.lastActionAt),
                    reclaimedLabel = app.lastReclaimedBytes
                        ?.let { Formatters.memory(context, it) },
                    onToggle = { viewModel.setEnabled(app.packageName, it) },
                    onUnblock = { viewModel.unblock(app) },
                    onStopNow = { viewModel.stopNow(app) },
                    onSwipeToSetDelay = { editandoAtrasoDe = app.packageName },
                    onTapDelayClock = { editandoAtrasoDe = app.packageName }
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
            title = stringResource(R.string.add_to_blocked),
            excludeRestricted = false,
            excludeBlocked = true,
            onDismiss = { pickerOpen = false },
            onConfirm = { apps ->
                viewModel.addApps(apps)
                pickerOpen = false
            }
        )
    }

    // Busca o app fresco na lista atual pelo nome do pacote, em vez de usar
    // uma cópia guardada no momento do arraste — assim o diálogo sempre
    // reflete o valor mais recente salvo no banco, mesmo que o usuário abra
    // o ajuste várias vezes seguidas rapidamente.
    editandoAtrasoDe?.let { pacote ->
        val app = state.apps.find { it.packageName == pacote }
        if (app != null) {
            AjusteAtrasoDialog(
                appName = app.appName,
                valorAtualSegundos = app.delaySeconds,
                onSalvar = { segundos ->
                    viewModel.setDelay(app.packageName, segundos)
                    editandoAtrasoDe = null
                },
                onRemover = {
                    viewModel.setDelay(app.packageName, null)
                    editandoAtrasoDe = null
                },
                onCancelar = { editandoAtrasoDe = null }
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String?,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    color = valueColor
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = valueColor,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedAppCard(
    app: BlockedAppEntity,
    lastAttempt: String,
    reclaimedLabel: String?,
    onToggle: (Boolean) -> Unit,
    onUnblock: () -> Unit,
    onStopNow: () -> Unit,
    onSwipeToSetDelay: () -> Unit,
    onTapDelayClock: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { valor ->
            if (valor == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToSetDelay()
            }
            // Sempre falso: o cartão nunca é de fato removido pelo gesto —
            // arrastar só abre o diálogo de atraso e volta pro lugar.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(end = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = stringResource(R.string.blocked_delay_set),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
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
                StatusPill(
                    text = if (app.isEnabled) {
                        stringResource(R.string.badge_blocked)
                    } else {
                        stringResource(R.string.badge_paused)
                    },
                    color = if (app.isEnabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
                // O relógio só aparece quando este app já tem um atraso
                // configurado — arrastar o cartão para a esquerda é como se
                // configura da primeira vez. Tocar aqui reabre o mesmo
                // diálogo para ajustar o valor ou remover o atraso. Fica no
                // canto, longe do "Encerrar agora" lá embaixo, para não
                // confundir as duas ações.
                if (app.delaySeconds != null && app.delaySeconds > 0) {
                    IconButton(
                        onClick = onTapDelayClock,
                        modifier = Modifier.size(28.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = stringResource(
                                R.string.blocked_delay_edit,
                                formatarDuracao(app.delaySeconds)
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassInner(MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.last_attempt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = lastAttempt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                // A barra usa a memória realmente recuperada na última ação.
                // Sem medição disponível, fica vazia e o rótulo diz "sem dados".
                val bytes = app.lastReclaimedBytes
                MetricBar(
                    progress = bytes?.let {
                        (it / (1024f * 1024f * 1024f)).coerceIn(0f, 1f)
                    } ?: 0f,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.memory_reclaimed_last),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = reclaimedLabel ?: stringResource(R.string.no_data_yet),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .glassInner(MaterialTheme.shapes.small)
                        .clickable(onClick = onUnblock)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.unblock),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
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

            Text(
                text = stringResource(R.string.stop_now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onStopNow)
            )
        }
    }
    }
}

/** "90" segundos vira "1m 30s"; entradas menores que um minuto ficam só em segundos. */
private fun formatarDuracao(totalSegundos: Int): String {
    val horas = totalSegundos / 3600
    val minutos = (totalSegundos % 3600) / 60
    val segundos = totalSegundos % 60
    return buildString {
        if (horas > 0) append("${horas}h ")
        if (minutos > 0 || horas > 0) append("${minutos}m ")
        if (horas == 0) append("${segundos}s")
    }.trim()
}

/**
 * Diálogo para configurar (ou remover) o atraso de um app bloqueado
 * específico — quanto tempo esperar, depois que ele sai de primeiro plano,
 * antes de encerrar de verdade.
 */
@Composable
private fun AjusteAtrasoDialog(
    appName: String,
    valorAtualSegundos: Int?,
    onSalvar: (Int) -> Unit,
    onRemover: () -> Unit,
    onCancelar: () -> Unit
) {
    var unidadeIndex by remember(valorAtualSegundos) {
        mutableStateOf(
            when {
                valorAtualSegundos == null -> 0
                valorAtualSegundos % 60 == 0 && valorAtualSegundos >= 60 -> 1
                else -> 0
            }
        )
    }
    val valorInicial = when (unidadeIndex) {
        1 -> (valorAtualSegundos ?: 60) / 60
        else -> valorAtualSegundos ?: 30
    }
    var texto by remember(valorAtualSegundos) { mutableStateOf(valorInicial.toString()) }
    val unidades = listOf(
        stringResource(R.string.delay_unit_seconds),
        stringResource(R.string.delay_unit_minutes)
    )

    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = glassContainerColor(),
        title = { Text(stringResource(R.string.blocked_delay_dialog_title, appName)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.blocked_delay_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { novo -> if (novo.all { it.isDigit() }) texto = novo },
                    label = { Text(stringResource(R.string.blocked_delay_value_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    unidades.forEachIndexed { index, rotulo ->
                        SegmentedButton(
                            selected = unidadeIndex == index,
                            onClick = { unidadeIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index, count = unidades.size
                            )
                        ) { Text(rotulo) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val numero = texto.toIntOrNull()?.coerceAtLeast(1) ?: return@TextButton
                    val segundos = when (unidadeIndex) {
                        1 -> numero * 60
                        else -> numero
                    }
                    onSalvar(segundos)
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (valorAtualSegundos != null) {
                    TextButton(onClick = onRemover) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.blocked_delay_remove),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
