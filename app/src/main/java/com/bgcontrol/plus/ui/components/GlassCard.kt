package com.bgcontrol.plus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.bgcontrol.plus.ui.theme.Glass
import com.bgcontrol.plus.ui.theme.glassPanelTint
import com.bgcontrol.plus.ui.theme.glassSurface

/** Cartão de vidro — substitui o Card opaco em todas as telas. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    tint: Color = glassPanelTint(),
    alpha: Float = Glass.PANEL_ALPHA,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .glassSurface(shape = shape, tint = tint, alpha = alpha)
    Column(modifier = if (onClick != null) base.clickable(onClick = onClick) else base) {
        content()
    }
}
