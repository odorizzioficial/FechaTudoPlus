package com.bgcontrol.plus.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Camadas de vidro do "Kinetic Utility".
 *
 * O efeito é montado com translucidez, gradiente e borda luminosa — sem
 * dependências externas, o que mantém o app funcionando de API 26 em diante.
 * O fundo com brilhos suaves existe para que a translucidez tenha o que revelar.
 */
object Glass {
    /** Opacidade dos painéis principais (cartões). */
    const val PANEL_ALPHA = 0.76f

    /** Opacidade das superfícies flutuantes (barra inferior, selos). */
    const val FLOATING_ALPHA = 0.72f

    /** Sem vidro, as superfícies ficam sólidas. */
    const val SOLID_ALPHA = 1f
}

/**
 * Liga ou desliga o vidro em todo o app. É lido por [glassSurface], então vale
 * para cartões, selos, folhas e diálogos de todas as telas de uma vez.
 */
val LocalGlassEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/** Intensidade escolhida pelo usuário, de 0 a 100. */
val LocalGlassIntensity: ProvidableCompositionLocal<Int> = compositionLocalOf { 65 }

/**
 * O vidro precisa saber em que tema está. No escuro a luz vem de cima em branco;
 * no claro um brilho branco sobre superfície clara simplesmente desaparece, e a
 * separação passa a vir de uma borda escura e de painéis mais opacos.
 */
val LocalDarkTheme: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

@Composable
fun ProvideGlass(
    enabled: Boolean,
    intensity: Int,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGlassEnabled provides enabled,
        LocalGlassIntensity provides intensity.coerceIn(0, 100),
        LocalDarkTheme provides darkTheme,
        content = content
    )
}

/** Superfície-base dos painéis: contêiner no escuro, branco puro no claro. */
@Composable
fun glassPanelTint(): Color = if (LocalDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainer
} else {
    MaterialTheme.colorScheme.surfaceContainerLowest
}

/**
 * Superfície de vidro: fundo translúcido, brilho na aresta superior e borda de luz.
 * O brilho é o que dá o aspecto da barra inferior — luz batendo na quina do vidro.
 *
 * [accent] marca superfícies coloridas (selos e botões). Nelas a opacidade nunca
 * é forçada para 1 quando o vidro é desligado: se fosse, o fundo ficaria da
 * mesma cor do texto e o rótulo sumiria.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    tint: Color = glassPanelTint(),
    alpha: Float = Glass.PANEL_ALPHA,
    borderAlpha: Float = 0.16f,
    accent: Boolean = false
): Modifier {
    val comVidro = LocalGlassEnabled.current
    val escuro = LocalDarkTheme.current
    val fator = if (comVidro) LocalGlassIntensity.current / 100f else 0f

    // No tema claro os painéis precisam ficar bem mais opacos: a translucidez do
    // escuro deixaria o cartão indistinguível do fundo.
    val alvo = if (escuro) alpha else (alpha + 0.40f).coerceAtMost(0.96f)

    // A 0% a superfície fica sólida; a 100% chega à translucidez de projeto.
    // Superfícies coloridas (accent) mantêm a opacidade: se ficassem sólidas,
    // o fundo assumiria a cor do texto e o rótulo desapareceria.
    val opacidade = if (accent) alpha else lerp(Glass.SOLID_ALPHA, alvo, fator)

    // A borda é luz no escuro e sombra no claro.
    val corBorda = if (escuro) Color.White else Color.Black
    val luz = borderAlpha * (0.35f + 0.65f * fator) * (if (escuro) 1f else 0.85f)

    var resultado = this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = (opacidade + 0.10f).coerceAtMost(1f)),
                    tint.copy(alpha = (opacidade - 0.08f).coerceAtLeast(0f))
                )
            ),
            shape = shape
        )

    // O brilho da aresta superior só faz sentido sobre superfície escura.
    if (comVidro && escuro && fator > 0f) {
        resultado = resultado.background(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.13f * fator),
                0.30f to Color.White.copy(alpha = 0.03f * fator),
                0.55f to Color.Transparent
            ),
            shape = shape
        )
    }

    return resultado.border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            if (escuro) {
                listOf(
                    corBorda.copy(alpha = luz * 1.6f),
                    corBorda.copy(alpha = luz * 0.9f),
                    corBorda.copy(alpha = luz * 0.25f)
                )
            } else {
                // No claro a borda é uniforme: um contorno constante separa o
                // cartão do fundo melhor que um degradê que some embaixo.
                listOf(
                    corBorda.copy(alpha = luz * 0.9f),
                    corBorda.copy(alpha = luz * 1.1f)
                )
            }
        ),
        shape = shape
    )
}

/** Superfície interna de um cartão (as "janelas" dentro dos cartões). */
@Composable
fun Modifier.glassInner(shape: Shape): Modifier = glassSurface(
    shape = shape,
    tint = if (LocalDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
    alpha = 0.82f,
    borderAlpha = 0.10f
)

/**
 * Cor de fundo para diálogos e folhas.
 *
 * Aqui não há translucidez: listas longas sobre um fundo semitransparente ficam
 * ilegíveis, e ler a lista importa mais que o efeito.
 */
@Composable
fun glassContainerColor(): Color = if (LocalDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else {
    MaterialTheme.colorScheme.surfaceContainerLowest
}

/**
 * Fundo do aplicativo: base neutra do design com dois brilhos difusos
 * (violeta e ciano) que dão profundidade ao vidro sem alterar a paleta.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Os brilhos servem para o vidro ter o que revelar. No claro eles precisam
    // ser bem mais discretos, senão a tela vira uma mancha lavada.
    val forca = if (LocalDarkTheme.current) 1f else 0.35f
    Box(modifier = modifier.background(scheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.16f * forca),
                            Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            scheme.tertiaryContainer.copy(alpha = 0.12f * forca),
                            Color.Transparent
                        ),
                        center = Offset(1400f, 2600f),
                        radius = 1400f
                    )
                )
        )
        content()
    }
}
