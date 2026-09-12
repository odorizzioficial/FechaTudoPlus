package com.bgcontrol.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.data.entities.ScheduleAppEntity
import com.bgcontrol.plus.data.entities.ScheduleGroupEntity
import com.bgcontrol.plus.data.entities.ScheduleMode
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.ui.components.AppIconWithBadge
import com.bgcontrol.plus.ui.components.AppPickerSheet
import com.bgcontrol.plus.ui.components.AppTab
import com.bgcontrol.plus.ui.components.GlassActionButton
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.ScreenHeader
import com.bgcontrol.plus.ui.components.StatusPill
import com.bgcontrol.plus.ui.components.accent
import com.bgcontrol.plus.ui.components.bottomBarContentPadding
import com.bgcontrol.plus.ui.theme.glassInner
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.ScheduleViewModel
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Date

/** O ícone e a cor de cada modo, usados no cartão e na escolha. */
@Composable
private fun ScheduleMode.icone(): ImageVector = when (this) {
    ScheduleMode.FORCE_STOP -> Icons.Rounded.PowerSettingsNew
    ScheduleMode.BLOCK -> Icons.Rounded.Block
    ScheduleMode.RESTRICT -> Icons.Rounded.Shield
    ScheduleMode.FREEZE -> Icons.Rounded.AcUnit
}

@Composable
private fun ScheduleMode.cor(): Color = when (this) {
    ScheduleMode.FORCE_STOP -> AppTab.RUNNING.accent()
    ScheduleMode.BLOCK -> AppTab.BLOCKED.accent()
    ScheduleMode.RESTRICT -> AppTab.RESTRICTED.accent()
    ScheduleMode.FREEZE -> AppTab.FROZEN.accent()
}

@Composable
private fun ScheduleMode.rotulo(): String = stringResource(
    when (this) {
        ScheduleMode.FORCE_STOP -> R.string.mode_force_stop
        ScheduleMode.BLOCK -> R.string.mode_block
        ScheduleMode.RESTRICT -> R.string.mode_restrict
        ScheduleMode.FREEZE -> R.string.mode_freeze
    }
)

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = viewModel(factory = AppViewModelFactories.schedule)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // O assistente tem três etapas: nome e apps, ação, horário.
    var etapa by remember { mutableStateOf(0) }
    var nome by remember { mutableStateOf("") }
    var modo by remember { mutableStateOf(ScheduleMode.FORCE_STOP) }
    var escolhidos by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var grupoParaAdicionar by remember { mutableStateOf<Long?>(null) }
    // Preenchido quando o usuário toca em "Alterar": o assistente salva no
    // grupo existente em vez de criar outro.
    var editando by remember { mutableStateOf<ScheduleGroupEntity?>(null) }

    fun fecharAssistente() {
        etapa = 0
        nome = ""
        modo = ScheduleMode.FORCE_STOP
        escolhidos = emptyList()
        editando = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomBarContentPadding().calculateBottomPadding() + 76.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.schedule_title),
                    subtitle = stringResource(R.string.schedule_description),
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }

            if (!state.exactAlarmsAllowed) {
                item {
                    LimitationCard(
                        text = stringResource(R.string.exact_alarm_warning),
                        actionLabel = null,
                        onAction = null
                    )
                }
            }

            if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.schedule_empty_title),
                        description = stringResource(R.string.schedule_empty_description)
                    )
                }
            }

            items(state.groups, key = { it.id }) { grupo ->
                GroupCard(
                    group = grupo,
                    apps = state.appsByGroup[grupo.id].orEmpty(),
                    nextRun = DateFormat
                        .getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                        .format(Date(viewModel.nextRun(grupo))),
                    onToggle = { viewModel.setEnabled(grupo, it) },
                    onDelete = { viewModel.deleteGroup(grupo) },
                    onAddApps = { grupoParaAdicionar = grupo.id },
                    onEdit = {
                        editando = grupo
                        nome = grupo.name
                        modo = grupo.scheduleMode
                        escolhidos = emptyList()
                        etapa = 1
                    },
                    onRemoveApp = { viewModel.removeApp(grupo.id, it) }
                )
            }
        }

        GlassActionButton(
            text = stringResource(R.string.new_group),
            icon = Icons.Rounded.Add,
            tint = AppTab.SCHEDULE.accent(),
            contentColor = AppTab.SCHEDULE.accent(),
            onClick = {
                nome = ""
                escolhidos = emptyList()
                modo = ScheduleMode.FORCE_STOP
                etapa = 1
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = bottomBarContentPadding().calculateBottomPadding()
                )
        )
    }

    when (etapa) {
        1 -> CreateGroupStep(
            name = nome,
            onNameChange = { nome = it },
            initialSelection = editando
                ?.let { grupo -> state.appsByGroup[grupo.id].orEmpty().map { it.packageName } }
                .orEmpty(),
            confirmLabel = stringResource(
                if (editando == null) R.string.create_group else R.string.continue_label
            ),
            onDismiss = { fecharAssistente() },
            onNext = { apps ->
                escolhidos = apps
                etapa = 2
            }
        )

        2 -> ChooseModeStep(
            mode = modo,
            onModeChange = { modo = it },
            onDismiss = { fecharAssistente() },
            onNext = { etapa = 3 },
            iconOf = { it.icone() },
            colorOf = { it.cor() },
            labelOf = { it.rotulo() }
        )

        3 -> ScheduleTimeStep(
            groupName = nome,
            accent = modo.cor(),
            initialHour = editando?.hour,
            initialMinute = editando?.minute,
            initialSecond = editando?.second ?: 0,
            initialDays = editando?.daysOfWeek ?: 0,
            initialRepeat = editando?.repeatEnabled ?: true,
            initialDate = editando?.runAtDate,
            initialInterval = editando?.intervalSeconds,
            confirmLabel = stringResource(
                if (editando == null) R.string.save_time else R.string.save_changes
            ),
            onDismiss = { fecharAssistente() },
            onSave = { hora, minuto, segundo, dias, repetir, data, intervalo ->
                val alvo = editando
                if (alvo == null) {
                    viewModel.createGroup(
                        nome, modo, hora, minuto, segundo, dias,
                        repetir, data, intervalo, escolhidos
                    )
                } else {
                    viewModel.updateGroup(
                        alvo, nome, modo, hora, minuto, segundo, dias,
                        repetir, data, intervalo, escolhidos
                    )
                }
                fecharAssistente()
            }
        )
    }

    grupoParaAdicionar?.let { groupId ->
        AppPickerSheet(
            title = stringResource(R.string.add_app),
            excludeRestricted = false,
            excludeBlocked = false,
            onDismiss = { grupoParaAdicionar = null },
            onConfirm = { apps ->
                viewModel.addApps(groupId, apps)
                grupoParaAdicionar = null
            }
        )
    }
}

@Composable
private fun GroupCard(
    group: ScheduleGroupEntity,
    apps: List<ScheduleAppEntity>,
    nextRun: String,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAddApps: () -> Unit,
    onEdit: () -> Unit,
    onRemoveApp: (String) -> Unit
) {
    // A lista de aplicativos nasce fechada; o cartão mostra só a contagem.
    var aberto by remember { mutableStateOf(false) }
    val giro by animateFloatAsState(targetValue = if (aberto) 180f else 0f, label = "seta")
    val cor = group.scheduleMode.cor()
    val context = LocalContext.current

    GlassCard(onClick = { aberto = !aberto }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(cor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = group.scheduleMode.icone(),
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // A contagem sobe para o topo do cartão: é o número que
                        // diz de imediato quantos aplicativos o grupo trata,
                        // sem precisar abrir a lista.
                        StatusPill(
                            text = apps.size.toString(),
                            color = cor,
                            showDot = false,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        text = String.format(
                            "%02d:%02d:%02d",
                            group.hour,
                            group.minute,
                            group.second
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = cor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Alterar fica acima do modo, para editar sem refazer o grupo.
                    Text(
                        text = stringResource(R.string.edit_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onEdit)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    StatusPill(
                        text = group.scheduleMode.rotulo(),
                        color = cor,
                        showDot = false
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassInner(MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = diasDaSemana(group),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = nextRun,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.resources.getQuantityString(
                        R.plurals.apps_in_group,
                        apps.size,
                        apps.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.tap_to_see_apps),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .rotate(giro)
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = group.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = cor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            AnimatedVisibility(visible = aberto) {
                Column {
                    apps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconWithBadge(packageName = app.packageName, size = 32.dp)
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                            )
                            IconButton(onClick = { onRemoveApp(app.packageName) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.add_app),
                        style = MaterialTheme.typography.labelMedium,
                        color = cor,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onAddApps)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/** Texto da repetição: "Todo dia" ou as iniciais dos dias marcados. */
@Composable
private fun diasDaSemana(group: ScheduleGroupEntity): String {
    group.intervalSeconds?.let { total ->
        val (valor, unidade) = when {
            total % 3600 == 0 -> total / 3600 to R.string.unit_hours
            total % 60 == 0 -> total / 60 to R.string.unit_minutes
            else -> total to R.string.unit_seconds
        }
        return stringResource(R.string.interval_every, valor, stringResource(unidade))
    }
    if (!group.repeatEnabled) {
        val data = group.runAtDate ?: return stringResource(R.string.schedule_daily)
        val formato = DateFormat.getDateInstance(DateFormat.MEDIUM)
        return stringResource(R.string.run_once_on, formato.format(Date(data)))
    }
    if (group.daysOfWeek == 0) return stringResource(R.string.schedule_daily)
    val curtos = DateFormatSymbols.getInstance().shortWeekdays
    return group.selectedDays.joinToString(" · ") { curtos[it] }
}
