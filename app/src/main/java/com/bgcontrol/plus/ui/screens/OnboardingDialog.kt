package com.bgcontrol.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgcontrol.plus.R
import com.bgcontrol.plus.shizuku.ShizukuState
import com.bgcontrol.plus.ui.theme.glassContainerColor
import com.bgcontrol.plus.viewmodel.AppViewModelFactories
import com.bgcontrol.plus.viewmodel.SettingsViewModel

/**
 * Primeira abertura: apresenta as três autorizações de que o app depende.
 *
 * Cada linha mostra o estado real e abre o fluxo oficial do Android quando
 * tocada. O usuário pode sair a qualquer momento em "Agora não" — nada é
 * obrigatório e nada é concedido em silêncio.
 */
@Composable
fun OnboardingDialog(
    onFinish: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactories.settings)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Ao voltar de cada tela do sistema, os estados são relidos.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = {},
        containerColor = glassContainerColor(),
        title = { Text(stringResource(R.string.onboarding_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                StepRow(
                    icon = Icons.Rounded.Visibility,
                    label = stringResource(R.string.grant_usage_access),
                    done = state.hasUsageAccess,
                    onClick = viewModel::openUsageAccessSettings
                )
                StepRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = stringResource(R.string.battery_optimization),
                    done = state.batteryOptimized,
                    onClick = viewModel::requestBatteryExemption
                )
                StepRow(
                    icon = Icons.Rounded.Adb,
                    label = stringResource(R.string.activate_shizuku),
                    done = state.shizukuState == ShizukuState.READY,
                    onClick = viewModel::activateShizuku
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onFinish) {
                Text(stringResource(R.string.onboarding_finish))
            }
        },
        dismissButton = {
            TextButton(onClick = onFinish) {
                Text(
                    text = stringResource(R.string.onboarding_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun StepRow(
    icon: ImageVector,
    label: String,
    done: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !done, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (done) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (done) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    if (done) R.string.status_active else R.string.pending
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (done) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = if (done) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
