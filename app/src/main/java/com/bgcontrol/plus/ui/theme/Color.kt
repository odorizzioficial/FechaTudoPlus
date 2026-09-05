package com.bgcontrol.plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta extraída literalmente do DESIGN.md ("Kinetic Utility").
 * Nenhum valor deve ser alterado: o tema escuro é a referência visual do aplicativo.
 */
object Palette {
    // Escuro (padrão)
    val Surface = Color(0xFF141317)
    val SurfaceDim = Color(0xFF141317)
    val SurfaceBright = Color(0xFF3A383D)
    val SurfaceContainerLowest = Color(0xFF0E0E11)
    val SurfaceContainerLow = Color(0xFF1C1B1F)
    val SurfaceContainer = Color(0xFF201F23)
    val SurfaceContainerHigh = Color(0xFF2B292D)
    val SurfaceContainerHighest = Color(0xFF353438)
    val OnSurface = Color(0xFFE5E1E7)
    val OnSurfaceVariant = Color(0xFFCAC4D0)
    val InverseSurface = Color(0xFFE5E1E7)
    val InverseOnSurface = Color(0xFF313034)
    val Outline = Color(0xFF948F9A)
    val OutlineVariant = Color(0xFF49454F)
    val SurfaceTint = Color(0xFFD0BCFF)
    val Primary = Color(0xFFE9DDFF)
    val OnPrimary = Color(0xFF37265E)
    val PrimaryContainer = Color(0xFFD0BCFF)
    val OnPrimaryContainer = Color(0xFF594983)
    val InversePrimary = Color(0xFF665590)
    val Secondary = Color(0xFFD0BCFF)
    val OnSecondary = Color(0xFF381E72)
    val SecondaryContainer = Color(0xFF4F378A)
    val OnSecondaryContainer = Color(0xFFC0A7FF)
    val Tertiary = Color(0xFF9BEFFF)
    val OnTertiary = Color(0xFF00363D)
    val TertiaryContainer = Color(0xFF00DAF3)
    val OnTertiaryContainer = Color(0xFF005B66)
    val ErrorColor = Color(0xFFFFB4AB)
    val OnErrorColor = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val Background = Color(0xFF141317)
    val OnBackground = Color(0xFFE5E1E7)
    val SurfaceVariant = Color(0xFF353438)

    // Claro — mesmo sistema tonal, primário mais saturado (#6750A4) conforme DESIGN.md
    val LightPrimary = Color(0xFF6750A4)
    val LightOnPrimary = Color(0xFFFFFFFF)
    // Mesma lógica do tertiaryContainer: é usado como tinta de ícones e botões.
    val LightPrimaryContainer = Color(0xFF6750A4)
    val LightOnPrimaryContainer = Color(0xFFFFFFFF)
    val LightSecondary = Color(0xFF625B71)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFD7C7F5)
    val LightOnSecondaryContainer = Color(0xFF211047)
    val LightTertiary = Color(0xFF00687A)
    val LightOnTertiary = Color(0xFFFFFFFF)

    // O app usa tertiaryContainer como cor de texto e ícone nos selos ("Ativo",
    // "APLICATIVOS", memória recuperada). No claro isso precisa ser um tom
    // escuro: o ciano pálido do Material some sobre fundo branco.
    val LightTertiaryContainer = Color(0xFF00616F)
    val LightOnTertiaryContainer = Color(0xFFFFFFFF)
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)
    // Fundo levemente mais escuro que os cartões, para que eles se destaquem.
    val LightBackground = Color(0xFFF4EFF7)
    val LightOnBackground = Color(0xFF1D1B20)
    val LightSurface = Color(0xFFF4EFF7)
    val LightOnSurface = Color(0xFF1D1B20)
    val LightSurfaceVariant = Color(0xFFE7E0EB)
    val LightOnSurfaceVariant = Color(0xFF49454E)
    val LightOutline = Color(0xFF7A757F)
    val LightOutlineVariant = Color(0xFFCAC4CF)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFF7F2FA)
    val LightSurfaceContainer = Color(0xFFF2ECF4)
    val LightSurfaceContainerHigh = Color(0xFFECE6EE)
    val LightSurfaceContainerHighest = Color(0xFFE6E0E9)

    // Cor de cada aba: a mesma tinta identifica a aba, o ícone do menu de três
    // pontinhos e as ações relacionadas, no escuro e no claro.
    val AccentRunning = Color(0xFFD0BCFF)
    val AccentRestricted = Color(0xFF00DAF3)
    val AccentBlocked = Color(0xFFFFB4AB)
    val AccentSchedule = Color(0xFFFFD479)
    val AccentSettings = Color(0xFF9BEFFF)

    val LightAccentRunning = Color(0xFF6750A4)
    val LightAccentRestricted = Color(0xFF00616F)
    val LightAccentBlocked = Color(0xFFBA1A1A)
    val LightAccentSchedule = Color(0xFF8A5A00)
    val LightAccentSettings = Color(0xFF00687A)
}
