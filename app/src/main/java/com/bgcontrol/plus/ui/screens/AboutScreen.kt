package com.bgcontrol.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bgcontrol.plus.R
import com.bgcontrol.plus.ui.components.GlassCard
import com.bgcontrol.plus.ui.components.bottomBarContentPadding

/**
 * Sobre: um resumo de como o aplicativo funciona e, abaixo, as categorias
 * (Shizuku, termos, privacidade e segurança) em seções que abrem e fecham.
 * Uma tela só — o usuário lê o que quiser sem se perder em submenus.
 */
@Composable
fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.version_format, versionName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // O resumo fica sempre aberto: é a primeira coisa que alguém quer ler.
        item {
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.about_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Seções de como cada modo funciona
        item {
            AboutSection(
                icon = Icons.Rounded.PowerSettingsNew,
                tint = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.about_mode_running_title),
                body = stringResource(R.string.about_mode_running)
            )
        }
        item {
            AboutSection(
                icon = Icons.Rounded.Shield,
                tint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.about_mode_restricted_title),
                body = stringResource(R.string.about_mode_restricted)
            )
        }
        item {
            AboutSection(
                icon = Icons.Rounded.Block,
                tint = MaterialTheme.colorScheme.tertiary,
                title = stringResource(R.string.about_mode_blocked_title),
                body = stringResource(R.string.about_mode_blocked)
            )
        }
        item {
            AboutSection(
                icon = Icons.Rounded.AcUnit,
                tint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.about_mode_frozen_title),
                body = stringResource(R.string.about_mode_frozen)
            )
        }
        item {
            AboutSection(
                icon = Icons.Rounded.Schedule,
                tint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.about_mode_schedule_title),
                body = stringResource(R.string.about_mode_schedule)
            )
        }
        item {
            AboutSection(
                icon = Icons.Rounded.BubbleChart,
                tint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.about_mode_bubble_title),
                body = stringResource(R.string.about_mode_bubble)
            )
        }

        // Seção Shizuku
        item {
            AboutSection(
                icon = Icons.Rounded.Adb,
                tint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.about_shizuku_title),
                body = stringResource(R.string.about_shizuku)
            )
        }

        item {
            AboutSection(
                icon = Icons.Rounded.Gavel,
                tint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.about_terms_title),
                body = stringResource(R.string.about_terms)
            )
        }

        item {
            AboutSection(
                icon = Icons.Rounded.Lock,
                tint = MaterialTheme.colorScheme.tertiaryContainer,
                title = stringResource(R.string.about_privacy_title),
                body = stringResource(R.string.about_privacy)
            )
        }

        item {
            AboutSection(
                icon = Icons.Rounded.Shield,
                tint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.about_security_title),
                body = stringResource(R.string.about_security)
            )
        }
    }
}

@Composable
private fun AboutSection(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String
) {
    var aberto by remember { mutableStateOf(false) }
    val giro by animateFloatAsState(
        targetValue = if (aberto) 180f else 0f,
        label = "seta"
    )

    GlassCard(onClick = { aberto = !aberto }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(giro)
                )
            }

            AnimatedVisibility(visible = aberto) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
