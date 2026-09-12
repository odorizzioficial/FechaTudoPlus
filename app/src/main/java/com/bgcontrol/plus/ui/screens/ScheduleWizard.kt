package com.bgcontrol.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.data.entities.ScheduleMode
import com.bgcontrol.plus.schedule.ScheduleAlarms
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.ui.components.ActionPill
import com.bgcontrol.plus.ui.components.AppIconWithBadge
import com.bgcontrol.plus.ui.theme.glassContainerColor
import com.bgcontrol.plus.viewmodel.AppPickerViewModel
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date

/**
 * Passo 1 — nome do grupo e escolha dos aplicativos, na mesma janela.
 * A busca e o botão de apps do sistema ficam ali dentro, sem abrir outra tela.
 */
@Composable
fun CreateGroupStep(
    name: String,
    onNameChange: (String) -> Unit,
    initialSelection: List<String> = emptyList(),
    confirmLabel: String = stringResource(R.string.create_group),
    onDismiss: () -> Unit,
    onNext: (List<InstalledApp>) -> Unit,
    viewModel: AppPickerViewModel = viewModel(factory = AppPickerViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // O seletor é compartilhado entre aberturas, então começa sempre zerado:
    // busca vazia e nenhuma seleção herdada do grupo anterior.
    LaunchedEffect(Unit) {
        viewModel.reset(initialSelection)
        // Ao alterar um grupo, os apps que ele já tem precisam aparecer aqui
        // marcados. Sem isto, os que não estavam na lista curta (apps do
        // sistema, por exemplo) sumiam ao salvar e o grupo ficava sem eles —
        // era assim que a contagem de aplicativos zerava depois de uma edição.
        viewModel.load(
            excludeRestricted = false,
            excludeBlocked = false,
            alwaysVisible = initialSelection.toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainerColor(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.create_group),
                    modifier = Modifier.weight(1f)
                )
                ActionPill(
                    text = stringResource(R.string.system_apps_button),
                    icon = Icons.Rounded.Android,
                    color = if (state.includeSystem) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = viewModel::toggleSystemApps
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_by_name)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        val marcado = app.packageName in state.selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { viewModel.toggle(app.packageName) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconWithBadge(packageName = app.packageName, size = 32.dp)
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                            )
                            Icon(
                                imageVector = if (marcado) {
                                    Icons.Rounded.Check
                                } else {
                                    Icons.Rounded.Add
                                },
                                contentDescription = null,
                                tint = if (marcado) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onNext(viewModel.selectedApps()) },
                // Nome é obrigatório: sem ele o grupo vira "FORCE_STOP" na lista.
                enabled = state.selected.isNotEmpty() && name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

}

/** Passo 2 — o que fazer com os aplicativos do grupo. */
@Composable
fun ChooseModeStep(
    mode: ScheduleMode,
    onModeChange: (ScheduleMode) -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    iconOf: @Composable (ScheduleMode) -> ImageVector,
    colorOf: @Composable (ScheduleMode) -> Color,
    labelOf: @Composable (ScheduleMode) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainerColor(),
        title = { Text(stringResource(R.string.choose_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScheduleMode.entries.forEach { opcao ->
                    val cor = colorOf(opcao)
                    val selecionado = opcao == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selecionado) {
                                    cor.copy(alpha = 0.20f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .clickable { onModeChange(opcao) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(cor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconOf(opcao),
                                contentDescription = null,
                                tint = cor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = labelOf(opcao),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selecionado) cor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    when (opcao) {
                                        ScheduleMode.FORCE_STOP -> R.string.mode_force_stop_desc
                                        ScheduleMode.BLOCK -> R.string.mode_block_desc
                                        ScheduleMode.RESTRICT -> R.string.mode_restrict_desc
                                        ScheduleMode.FREEZE -> R.string.mode_freeze_desc
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNext) { Text(stringResource(R.string.continue_label)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Passo 3 — relógio, segundos e dias da semana. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimeStep(
    groupName: String,
    accent: Color,
    initialHour: Int? = null,
    initialMinute: Int? = null,
    initialSecond: Int = 0,
    initialDays: Int = 0,
    initialRepeat: Boolean = true,
    initialDate: Long? = null,
    initialInterval: Int? = null,
    confirmLabel: String = stringResource(R.string.save_time),
    onDismiss: () -> Unit,
    onSave: (
        hour: Int,
        minute: Int,
        second: Int,
        daysOfWeek: Int,
        repeat: Boolean,
        runAtDate: Long?,
        intervalSeconds: Int?
    ) -> Unit
) {
    val agora = remember { Calendar.getInstance() }
    val timeState = rememberTimePickerState(
        initialHour = initialHour ?: agora.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialMinute ?: agora.get(Calendar.MINUTE),
        is24Hour = true
    )
    var segundos by remember { mutableStateOf(initialSecond.toFloat()) }
    var dias by remember { mutableStateOf(initialDays) }
    var repetir by remember { mutableStateOf(initialRepeat) }
    var dataEscolhida by remember { mutableStateOf(initialDate) }
    var seletorData by remember { mutableStateOf(false) }

    // Duas formas de agendar: em um horário do dia, ou a cada X tempo.
    var porIntervalo by remember { mutableStateOf(initialInterval != null) }
    var quantidade by remember { mutableStateOf(((initialInterval ?: 60) / 60).coerceAtLeast(1)) }
    var unidade by remember {
        mutableStateOf(
            when {
                initialInterval == null -> 1
                initialInterval % 3600 == 0 -> 2
                initialInterval % 60 == 0 -> 1
                else -> 0
            }
        )
    }

    // Iniciais dos dias vêm do sistema, então já saem no idioma do aparelho.
    val iniciais = remember {
        val curtos = DateFormatSymbols.getInstance().shortWeekdays
        (1..7).map { curtos[it].take(1).uppercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainerColor(),
        title = { Text(groupName.ifBlank { stringResource(R.string.tab_schedule) }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModoChip(
                        label = stringResource(R.string.mode_time_button),
                        selecionado = !porIntervalo,
                        cor = accent,
                        onClick = { porIntervalo = false },
                        modifier = Modifier.weight(1f)
                    )
                    ModoChip(
                        label = stringResource(R.string.mode_repeat_button),
                        selecionado = porIntervalo,
                        cor = accent,
                        onClick = { porIntervalo = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (porIntervalo) {
                    Text(
                        text = stringResource(R.string.interval_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = quantidade.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = accent
                        )
                        Slider(
                            value = quantidade.toFloat(),
                            onValueChange = { quantidade = it.toInt().coerceAtLeast(1) },
                            valueRange = 1f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            R.string.unit_seconds,
                            R.string.unit_minutes,
                            R.string.unit_hours
                        ).forEachIndexed { indice, rotulo ->
                            ModoChip(
                                label = stringResource(rotulo),
                                selecionado = unidade == indice,
                                cor = accent,
                                onClick = { unidade = indice },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.interval_min_warning),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {

                Text(
                    text = stringResource(R.string.schedule_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = accent,
                        periodSelectorSelectedContainerColor = accent.copy(alpha = 0.25f),
                        timeSelectorSelectedContainerColor = accent,
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.seconds_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%02d".format(segundos.toInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Slider(
                    value = segundos,
                    onValueChange = { segundos = it },
                    valueRange = 0f..59f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent
                    )
                )

                // Repetir ligado: dias da semana. Desligado: uma data só.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.repeat_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!repetir) {
                            Text(
                                text = stringResource(R.string.repeat_off_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = repetir,
                        onCheckedChange = { repetir = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = accent
                        )
                    )
                }

                if (!repetir) {
                    val formato = remember {
                        DateFormat.getDateInstance(DateFormat.MEDIUM)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { seletorData = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = dataEscolhida?.let { formato.format(Date(it)) }
                                ?: stringResource(R.string.choose_date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.weekdays_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    iniciais.forEachIndexed { indice, letra ->
                        val marcado = dias shr indice and 1 == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (marcado) {
                                        accent.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                                .clickable(enabled = repetir) {
                                    dias = dias xor (1 shl indice)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letra,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (marcado) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intervalo = if (porIntervalo) {
                        val fator = when (unidade) {
                            0 -> 1
                            2 -> 3600
                            else -> 60
                        }
                        (quantidade * fator).coerceAtLeast(ScheduleAlarms.MIN_INTERVALO_S)
                    } else {
                        null
                    }
                    val data = if (repetir || porIntervalo) {
                        null
                    } else {
                        // A data escolhida recebe a hora do relógio.
                        Calendar.getInstance().apply {
                            timeInMillis = dataEscolhida ?: System.currentTimeMillis()
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                            set(Calendar.SECOND, segundos.toInt())
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    onSave(
                        timeState.hour,
                        timeState.minute,
                        segundos.toInt(),
                        if (repetir && !porIntervalo) dias else 0,
                        repetir || porIntervalo,
                        data,
                        intervalo
                    )
                },
                enabled = porIntervalo || repetir || dataEscolhida != null
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (seletorData) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = dataEscolhida ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { seletorData = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dataEscolhida = dateState.selectedDateMillis
                        seletorData = false
                    }
                ) {
                    Text(stringResource(R.string.ok_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { seletorData = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

/** Chip de escolha, usado no seletor de modo e nas unidades de tempo. */
@Composable
private fun ModoChip(
    label: String,
    selecionado: Boolean,
    cor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selecionado) {
                    cor.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selecionado) cor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
