package com.bgcontrol.plus.ui.screens

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bgcontrol.plus.R
import com.bgcontrol.plus.preferences.AppSettings
import com.bgcontrol.plus.ui.components.AppPickerSheet
import com.bgcontrol.plus.ui.components.AppTab
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.StatusPill
import com.bgcontrol.plus.ui.components.accent
import com.bgcontrol.plus.ui.components.bottomBarContentPadding

/**
 * Notificações rápidas: as três formas de encerrar tudo sem abrir o aplicativo.
 */
@Composable
fun NotificationsScreen(
    settings: AppSettings,
    overlayAllowed: Boolean,
    notificationsAllowed: Boolean,
    onBack: () -> Unit,
    onQuickTile: () -> Unit,
    onPersistentChange: (Boolean) -> Unit,
    onBubbleChange: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onExcludedChange: (Set<String>) -> Unit,
    onBubbleOpacityChange: (Int) -> Unit,
    onBubbleSizeChange: (Int) -> Unit,
    onBubbleResetPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    var seletorApps by remember { mutableStateOf(false) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.notifications_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Bloco das Configurações Rápidas
        item {
            LinhaAcao(
                icon = Icons.Rounded.Dashboard,
                cor = AppTab.BLOCKED.accent(),
                titulo = stringResource(R.string.quick_tile_title),
                descricao = stringResource(R.string.quick_tile_subtitle),
                habilitado = !settings.quickTileAdded,
                onClick = onQuickTile
            ) {
                if (settings.quickTileAdded) {
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
        }

        // Notificação fixa
        item {
            LinhaAcao(
                icon = Icons.Rounded.Notifications,
                cor = AppTab.RUNNING.accent(),
                titulo = stringResource(R.string.persistent_notification),
                descricao = stringResource(R.string.persistent_notification_desc),
                onClick = { onPersistentChange(!settings.persistentNotification) }
            ) {
                Interruptor(
                    marcado = settings.persistentNotification,
                    cor = AppTab.RUNNING.accent(),
                    onChange = onPersistentChange
                )
            }
        }

        // Sem a permissão de notificações o serviço até sobe, mas o Android
        // descarta a notificação em silêncio — o usuário liga o interruptor e
        // não vê nada na barra. O aviso vermelho diz o que falta e resolve.
        if (!notificationsAllowed) {
            item {
                CartaoPermissao(
                    texto = stringResource(R.string.notification_permission_needed),
                    rotuloAcao = stringResource(R.string.grant_permission),
                    onAcao = onRequestNotifications
                )
            }
        }

        // Bolha flutuante
        item {
            LinhaAcao(
                icon = Icons.Rounded.BubbleChart,
                cor = AppTab.SCHEDULE.accent(),
                titulo = stringResource(R.string.floating_bubble),
                descricao = stringResource(R.string.floating_bubble_desc),
                onClick = {
                    if (!overlayAllowed) onRequestOverlay() else onBubbleChange(!settings.bubbleEnabled)
                }
            ) {
                if (!overlayAllowed) {
                    StatusPill(
                        text = stringResource(R.string.permission_missing),
                        color = MaterialTheme.colorScheme.error,
                        showDot = true
                    )
                } else {
                    Interruptor(
                        marcado = settings.bubbleEnabled,
                        cor = AppTab.SCHEDULE.accent(),
                        onChange = onBubbleChange
                    )
                }
            }
        }

        // A permissão de sobreposição é concedida pelo usuário, na tela do
        // Android. Enquanto ela faltar, o aviso fica vermelho e visível.
        if (!overlayAllowed) {
            item {
                CartaoPermissao(
                    texto = stringResource(R.string.overlay_permission_needed),
                    rotuloAcao = stringResource(R.string.overlay_permission),
                    onAcao = onRequestOverlay
                )
            }
        }

        if (settings.bubbleEnabled && overlayAllowed) {
            // Opacidade e tamanho ficam junto do interruptor, e o efeito
            // aparece na bolha no mesmo instante em que o dedo arrasta.
            item {
                // Aviso sobre o toque longo de 2 segundos
                GlassCard {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = AppTab.SCHEDULE.accent(),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.bubble_longpress_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }

            item {
                CartaoAjuste(
                    icone = Icons.Rounded.Opacity,
                    cor = AppTab.SCHEDULE.accent(),
                    titulo = stringResource(R.string.bubble_opacity),
                    valor = stringResource(R.string.percent_value, settings.bubbleOpacity),
                    posicao = settings.bubbleOpacity.toFloat(),
                    faixa = 0f..100f,
                    passos = 20,
                    onChange = { onBubbleOpacityChange(it.toInt()) }
                )
            }

            item {
                CartaoAjuste(
                    icone = Icons.Rounded.FormatSize,
                    cor = AppTab.SCHEDULE.accent(),
                    titulo = stringResource(R.string.bubble_size),
                    valor = stringResource(R.string.dp_value, settings.bubbleSize),
                    posicao = settings.bubbleSize.toFloat(),
                    faixa = 36f..96f,
                    passos = 11,
                    onChange = { onBubbleSizeChange(it.toInt()) }
                )
            }

            item {
                LinhaAcao(
                    icon = Icons.Rounded.CenterFocusStrong,
                    cor = AppTab.SCHEDULE.accent(),
                    titulo = stringResource(R.string.bubble_reset_position),
                    descricao = stringResource(R.string.bubble_reset_position_desc),
                    onClick = onBubbleResetPosition
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = AppTab.SCHEDULE.accent()
                    )
                }
            }

            item {
                LinhaAcao(
                    icon = Icons.Rounded.BubbleChart,
                    cor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titulo = stringResource(R.string.bubble_hidden_apps),
                    descricao = stringResource(
                        R.string.bubble_hidden_count,
                        settings.bubbleExcluded.size
                    ),
                    onClick = { seletorApps = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (seletorApps) {
        // manageMode: o que já está oculto chega marcado, e desmarcar remove de
        // verdade. Antes a folha só somava ao conjunto, então desmarcar um app
        // não tinha efeito nenhum e ele voltava marcado na abertura seguinte.
        AppPickerSheet(
            title = stringResource(R.string.bubble_hidden_apps),
            excludeRestricted = false,
            excludeBlocked = false,
            preSelected = settings.bubbleExcluded,
            manageMode = true,
            onDismiss = { seletorApps = false },
            onConfirm = {},
            onConfirmPackages = { pacotes ->
                onExcludedChange(pacotes)
                seletorApps = false
            }
        )
    }
}

/**
 * Aviso de permissão que falta. Vermelho de propósito: é o único jeito de o
 * usuário perceber que ligou o recurso e ele não vai funcionar até conceder.
 */
@Composable
private fun CartaoPermissao(texto: String, rotuloAcao: String, onAcao: () -> Unit) {
    GlassCard(
        tint = MaterialTheme.colorScheme.error,
        alpha = 0.22f
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = rotuloAcao,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAcao)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** Cartão com um controle deslizante e o valor atual à direita do título. */
@Composable
private fun CartaoAjuste(
    icone: ImageVector,
    cor: Color,
    titulo: String,
    valor: String,
    posicao: Float,
    faixa: ClosedFloatingPointRange<Float>,
    passos: Int,
    onChange: (Float) -> Unit
) {
    GlassCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                )
                Text(
                    text = valor,
                    style = MaterialTheme.typography.titleMedium,
                    color = cor
                )
            }
            Slider(
                value = posicao,
                onValueChange = onChange,
                valueRange = faixa,
                steps = passos,
                colors = SliderDefaults.colors(
                    thumbColor = cor,
                    activeTrackColor = cor,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

@Composable
private fun LinhaAcao(
    icon: ImageVector,
    cor: Color,
    titulo: String,
    descricao: String,
    onClick: () -> Unit,
    habilitado: Boolean = true,
    trailing: @Composable () -> Unit
) {
    GlassCard(onClick = if (habilitado) onClick else null) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

@Composable
private fun Interruptor(marcado: Boolean, cor: Color, onChange: (Boolean) -> Unit) {
    Switch(
        checked = marcado,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = cor,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}
