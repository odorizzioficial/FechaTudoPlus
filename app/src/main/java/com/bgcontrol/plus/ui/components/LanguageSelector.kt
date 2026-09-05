package com.bgcontrol.plus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bgcontrol.plus.R
import com.bgcontrol.plus.preferences.AppLanguage
import com.bgcontrol.plus.ui.theme.Glass
import com.bgcontrol.plus.ui.theme.glassSurface

/**
 * Seletor de idioma no topo da tela, ao lado do relógio do sistema.
 *
 * Arrastar para cima vai para o idioma anterior, para baixo vai para o próximo.
 * Um toque abre a lista completa de idiomas.
 */
@Composable
fun LanguageSelector(
    current: AppLanguage,
    onChange: (AppLanguage) -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val limite = remember(density) { with(density) { 28.dp.toPx() } }
    val dica = stringResource(R.string.language_hint)

    Row(
        modifier = modifier
            .semantics { contentDescription = dica }
            .glassSurface(
                shape = CircleShape,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = Glass.FLOATING_ALPHA,
                borderAlpha = 0.18f
            )
            .pointerInput(current) {
                var acumulado = 0f
                detectVerticalDragGestures(
                    onDragStart = { acumulado = 0f },
                    onDragEnd = {
                        when {
                            acumulado <= -limite -> onChange(AppLanguage.anterior(current))
                            acumulado >= limite -> onChange(AppLanguage.proximo(current))
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    acumulado += dragAmount
                }
            }
            .clickable(onClick = onOpenList)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = current.flag, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = current.tag.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
