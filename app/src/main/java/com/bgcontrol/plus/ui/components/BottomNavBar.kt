package com.bgcontrol.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bgcontrol.plus.R
import com.bgcontrol.plus.ui.theme.Glass
import com.bgcontrol.plus.ui.theme.LocalDarkTheme
import com.bgcontrol.plus.ui.theme.Palette
import com.bgcontrol.plus.ui.theme.glassSurface

/**
 * Os ícones são os mesmos do menu de três pontinhos da aba Em execução:
 * força parada, proteger e bloquear. Assim a ação e a aba falam a mesma língua.
 */
enum class AppTab(val icon: ImageVector, val labelRes: Int) {
    RUNNING(Icons.Rounded.PowerSettingsNew, R.string.tab_running),
    RESTRICTED(Icons.Rounded.Shield, R.string.tab_restricted),
    BLOCKED(Icons.Rounded.Block, R.string.tab_blocked),
    FROZEN(Icons.Rounded.AcUnit, R.string.tab_frozen),
    SCHEDULE(Icons.Rounded.Schedule, R.string.tab_schedule),
    SETTINGS(Icons.Rounded.Settings, R.string.tab_settings)
}

/**
 * A tinta da aba. É a mesma usada pelo ícone correspondente no menu de três
 * pontinhos, para que ação e destino sejam reconhecidos pela cor.
 */
@Composable
fun AppTab.accent(): Color = if (LocalDarkTheme.current) {
    when (this) {
        AppTab.RUNNING -> Palette.AccentRunning
        AppTab.RESTRICTED -> Palette.AccentRestricted
        AppTab.BLOCKED -> Palette.AccentBlocked
        AppTab.FROZEN -> Palette.AccentFrozen
        AppTab.SCHEDULE -> Palette.AccentSchedule
        AppTab.SETTINGS -> Palette.AccentSettings
    }
} else {
    when (this) {
        AppTab.RUNNING -> Palette.LightAccentRunning
        AppTab.RESTRICTED -> Palette.LightAccentRestricted
        AppTab.BLOCKED -> Palette.LightAccentBlocked
        AppTab.FROZEN -> Palette.LightAccentFrozen
        AppTab.SCHEDULE -> Palette.LightAccentSchedule
        AppTab.SETTINGS -> Palette.LightAccentSettings
    }
}

/**
 * Barra inferior flutuante em vidro.
 *
 * Cada aba ocupa a mesma fração da largura (weight) e o rótulo fica em uma
 * única linha, o que elimina o amontoado de texto da versão anterior.
 */
@Composable
fun BottomNavBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp + navInset.calculateBottomPadding()
            )
            .glassSurface(
                shape = RoundedCornerShape(28.dp),
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = Glass.FLOATING_ALPHA,
                borderAlpha = 0.20f
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { tab ->
            NavItem(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) }
            )
        }
    }
}

/**
 * Só os itens, sem moldura. É esta versão que é desenhada dentro do
 * LiquidGlassView: o vidro já cuida do fundo, da borda e da refração.
 */
@Composable
fun BottomNavContent(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { tab ->
            NavItem(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cor = tab.accent()
    val contentColor by animateColorAsState(
        targetValue = if (selected) cor else cor.copy(alpha = 0.55f),
        label = "nav-color"
    )
    val background by animateColorAsState(
        targetValue = if (selected) cor.copy(alpha = 0.20f) else Color.Transparent,
        label = "nav-bg"
    )
    val interactionSource = remember { MutableInteractionSource() }

    // Sem rótulo: o nome da aba já aparece no título da tela aberta, e sem o
    // texto sobra espaço para o ícone respirar.
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.labelRes),
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Espaço que o conteúdo precisa reservar para a barra flutuante. */
@Composable
fun bottomBarContentPadding(): PaddingValues = PaddingValues(
    bottom = 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
)
