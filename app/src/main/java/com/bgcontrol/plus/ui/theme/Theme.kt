package com.bgcontrol.plus.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bgcontrol.plus.preferences.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Palette.OnPrimary,
    primaryContainer = Palette.PrimaryContainer,
    onPrimaryContainer = Palette.OnPrimaryContainer,
    inversePrimary = Palette.InversePrimary,
    secondary = Palette.Secondary,
    onSecondary = Palette.OnSecondary,
    secondaryContainer = Palette.SecondaryContainer,
    onSecondaryContainer = Palette.OnSecondaryContainer,
    tertiary = Palette.Tertiary,
    onTertiary = Palette.OnTertiary,
    tertiaryContainer = Palette.TertiaryContainer,
    onTertiaryContainer = Palette.OnTertiaryContainer,
    error = Palette.ErrorColor,
    onError = Palette.OnErrorColor,
    errorContainer = Palette.ErrorContainer,
    onErrorContainer = Palette.OnErrorContainer,
    background = Palette.Background,
    onBackground = Palette.OnBackground,
    surface = Palette.Surface,
    onSurface = Palette.OnSurface,
    surfaceVariant = Palette.SurfaceVariant,
    onSurfaceVariant = Palette.OnSurfaceVariant,
    surfaceTint = Palette.SurfaceTint,
    inverseSurface = Palette.InverseSurface,
    inverseOnSurface = Palette.InverseOnSurface,
    outline = Palette.Outline,
    outlineVariant = Palette.OutlineVariant,
    surfaceBright = Palette.SurfaceBright,
    surfaceDim = Palette.SurfaceDim,
    surfaceContainerLowest = Palette.SurfaceContainerLowest,
    surfaceContainerLow = Palette.SurfaceContainerLow,
    surfaceContainer = Palette.SurfaceContainer,
    surfaceContainerHigh = Palette.SurfaceContainerHigh,
    surfaceContainerHighest = Palette.SurfaceContainerHighest
)

private val LightColors = lightColorScheme(
    primary = Palette.LightPrimary,
    onPrimary = Palette.LightOnPrimary,
    primaryContainer = Palette.LightPrimaryContainer,
    onPrimaryContainer = Palette.LightOnPrimaryContainer,
    secondary = Palette.LightSecondary,
    onSecondary = Palette.LightOnSecondary,
    secondaryContainer = Palette.LightSecondaryContainer,
    onSecondaryContainer = Palette.LightOnSecondaryContainer,
    tertiary = Palette.LightTertiary,
    onTertiary = Palette.LightOnTertiary,
    tertiaryContainer = Palette.LightTertiaryContainer,
    onTertiaryContainer = Palette.LightOnTertiaryContainer,
    error = Palette.LightError,
    onError = Palette.LightOnError,
    errorContainer = Palette.LightErrorContainer,
    onErrorContainer = Palette.LightOnErrorContainer,
    background = Palette.LightBackground,
    onBackground = Palette.LightOnBackground,
    surface = Palette.LightSurface,
    onSurface = Palette.LightOnSurface,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightOutlineVariant,
    surfaceContainerLowest = Palette.LightSurfaceContainerLowest,
    surfaceContainerLow = Palette.LightSurfaceContainerLow,
    surfaceContainer = Palette.LightSurfaceContainer,
    surfaceContainerHigh = Palette.LightSurfaceContainerHigh,
    surfaceContainerHighest = Palette.LightSurfaceContainerHighest
)

@Composable
fun BgControlTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    glassEnabled: Boolean = true,
    glassIntensity: Int = 65,
    content: @Composable () -> Unit
) {
    val dark = themeMode == ThemeMode.DARK
    val colors = if (dark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes
    ) {
        ProvideGlass(
            enabled = glassEnabled,
            intensity = glassIntensity,
            darkTheme = dark,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
