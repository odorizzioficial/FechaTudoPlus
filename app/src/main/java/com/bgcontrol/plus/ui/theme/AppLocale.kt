package com.bgcontrol.plus.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.bgcontrol.plus.preferences.AppLanguage
import java.util.Locale

/**
 * Aplica o idioma escolhido sem recriar a Activity.
 *
 * A troca via AppCompatDelegate.setApplicationLocales reinicia a tela, e é isso
 * que causava o piscar preto ao arrastar o seletor. Aqui trocamos apenas o
 * Context e a Configuration que o Compose usa para resolver as strings: a
 * recomposição acontece no mesmo quadro, sem reinício e sem piscada.
 */
@Composable
fun ProvideAppLocale(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val locale = remember(language) { Locale.forLanguageTag(language.tag) }

    val configuration = remember(language, context) {
        Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(configuration) {
        context.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content
    )
}
