package com.bgcontrol.plus.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT }

/** Idiomas suportados. A tag vazia significa "seguir o sistema". */
enum class AppLanguage(val tag: String, val displayName: String, val flag: String) {
    PT_BR("pt-BR", "Português (Brasil)", "\uD83C\uDDE7\uD83C\uDDF7"),
    PT_PT("pt-PT", "Português (Portugal)", "\uD83C\uDDF5\uD83C\uDDF9"),
    EN("en", "English", "\uD83C\uDDFA\uD83C\uDDF8"),
    ES("es", "Español", "\uD83C\uDDEA\uD83C\uDDF8"),
    RO("ro", "Română", "\uD83C\uDDF7\uD83C\uDDF4"),
    FR("fr", "Français", "\uD83C\uDDEB\uD83C\uDDF7"),
    ZH("zh-CN", "中文", "\uD83C\uDDE8\uD83C\uDDF3"),
    JA("ja", "日本語", "\uD83C\uDDEF\uD83C\uDDF5"),
    KO("ko", "한국어", "\uD83C\uDDF0\uD83C\uDDF7"),
    HI("hi", "हिन्दी", "\uD83C\uDDEE\uD83C\uDDF3");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: fromSystem()

        /**
         * Idioma inicial a partir da configuração do aparelho: primeiro tenta
         * casar país e idioma (pt-BR, pt-PT, zh-CN), depois só o idioma.
         * Sem correspondência, cai em inglês.
         */
        fun fromSystem(): AppLanguage {
            val locale = java.util.Locale.getDefault()
            val completo = "${locale.language}-${locale.country}"
            entries.firstOrNull { it.tag.equals(completo, ignoreCase = true) }
                ?.let { return it }
            entries.firstOrNull {
                it.tag.substringBefore('-').equals(locale.language, ignoreCase = true)
            }?.let { return it }
            return EN
        }

        fun proximo(atual: AppLanguage): AppLanguage =
            entries[(entries.indexOf(atual) + 1) % entries.size]

        fun anterior(atual: AppLanguage): AppLanguage =
            entries[(entries.indexOf(atual) - 1 + entries.size) % entries.size]
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.PT_BR,
    val blockerServiceEnabled: Boolean = true,
    val glassEnabled: Boolean = true,
    /** Intensidade do vidro, de 0 (sólido) a 100 (translucidez máxima). */
    val glassIntensity: Int = 65,
    val onboardingDone: Boolean = false,
    val defaultsSeeded: Boolean = false,
    val batteryDialogShown: Boolean = false,
    val quickTileAdded: Boolean = false,
    val persistentNotification: Boolean = false,
    val bubbleEnabled: Boolean = false,
    /** Pacotes em que a bolha não aparece. */
    val bubbleExcluded: Set<String> = emptySet(),
    /** Opacidade da bolha, de 20 (quase invisível) a 100 (sólida). */
    val bubbleOpacity: Int = 90,
    /** Diâmetro da bolha em dp. */
    val bubbleSize: Int = 56,
    /** Última posição em que o usuário largou a bolha, em pixels. */
    val bubbleX: Int = 0,
    val bubbleY: Int = 300,
    /** True depois que o convite de sobreposição já foi mostrado uma vez. */
    val overlayPromptShown: Boolean = false
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Configurações simples em DataStore. Persistem entre execuções e reinícios
 * do aparelho, sem tocar em rede.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val BLOCKER_ENABLED = booleanPreferencesKey("blocker_service_enabled")
        val GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
        val GLASS_INTENSITY = intPreferencesKey("glass_intensity")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val DEFAULTS_SEEDED = booleanPreferencesKey("defaults_seeded")
        val BATTERY_DIALOG = booleanPreferencesKey("battery_dialog_shown")
        val QUICK_TILE_ADDED = booleanPreferencesKey("quick_tile_added")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_EXCLUDED = stringSetPreferencesKey("bubble_excluded")
        val BUBBLE_OPACITY = intPreferencesKey("bubble_opacity")
        val BUBBLE_SIZE = intPreferencesKey("bubble_size")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
        val OVERLAY_PROMPT = booleanPreferencesKey("overlay_prompt_shown")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: "DARK") }
                .getOrDefault(ThemeMode.DARK),
            language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
            blockerServiceEnabled = prefs[Keys.BLOCKER_ENABLED] ?: true,
            glassEnabled = prefs[Keys.GLASS_ENABLED] ?: true,
            glassIntensity = (prefs[Keys.GLASS_INTENSITY] ?: 65).coerceIn(0, 100),
            onboardingDone = prefs[Keys.ONBOARDING] ?: false,
            defaultsSeeded = prefs[Keys.DEFAULTS_SEEDED] ?: false,
            batteryDialogShown = prefs[Keys.BATTERY_DIALOG] ?: false,
            quickTileAdded = prefs[Keys.QUICK_TILE_ADDED] ?: false,
            persistentNotification = prefs[Keys.PERSISTENT_NOTIFICATION] ?: false,
            bubbleEnabled = prefs[Keys.BUBBLE_ENABLED] ?: false,
            bubbleExcluded = prefs[Keys.BUBBLE_EXCLUDED] ?: emptySet(),
            bubbleOpacity = (prefs[Keys.BUBBLE_OPACITY] ?: 90).coerceIn(0, 100),
            bubbleSize = (prefs[Keys.BUBBLE_SIZE] ?: 56).coerceIn(36, 96),
            bubbleX = prefs[Keys.BUBBLE_X] ?: 0,
            bubbleY = prefs[Keys.BUBBLE_Y] ?: 300,
            overlayPromptShown = prefs[Keys.OVERLAY_PROMPT] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME] = mode.name }.let { }

    suspend fun setLanguage(language: AppLanguage) =
        context.dataStore.edit { it[Keys.LANGUAGE] = language.tag }.let { }

    suspend fun setGlassIntensity(percent: Int) =
        context.dataStore.edit { it[Keys.GLASS_INTENSITY] = percent.coerceIn(0, 100) }.let { }

    suspend fun setGlassEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.GLASS_ENABLED] = enabled }.let { }

    suspend fun setBlockerEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.BLOCKER_ENABLED] = enabled }.let { }

    suspend fun setOnboardingDone(done: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING] = done }.let { }

    suspend fun setDefaultsSeeded(done: Boolean) =
        context.dataStore.edit { it[Keys.DEFAULTS_SEEDED] = done }.let { }

    suspend fun setPersistentNotification(enabled: Boolean) =
        context.dataStore.edit { it[Keys.PERSISTENT_NOTIFICATION] = enabled }.let { }

    suspend fun setBubbleEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.BUBBLE_ENABLED] = enabled }.let { }

    suspend fun setBubbleExcluded(packages: Set<String>) =
        context.dataStore.edit { it[Keys.BUBBLE_EXCLUDED] = packages }.let { }

    suspend fun setBubbleOpacity(percent: Int) =
        context.dataStore.edit { it[Keys.BUBBLE_OPACITY] = percent.coerceIn(0, 100) }.let { }

    suspend fun setBubbleSize(dp: Int) =
        context.dataStore.edit { it[Keys.BUBBLE_SIZE] = dp.coerceIn(36, 96) }.let { }

    /**
     * Guarda onde o usuário largou a bolha. Sem isto ela voltava para o canto
     * superior a cada vez que o serviço a recriava — ao sair e voltar do app,
     * ao trocar de aplicativo ou ao reiniciar o aparelho.
     */
    suspend fun setBubblePosition(x: Int, y: Int) =
        context.dataStore.edit {
            it[Keys.BUBBLE_X] = x
            it[Keys.BUBBLE_Y] = y
        }.let { }

    suspend fun setOverlayPromptShown(shown: Boolean) =
        context.dataStore.edit { it[Keys.OVERLAY_PROMPT] = shown }.let { }

    suspend fun setQuickTileAdded(added: Boolean) =
        context.dataStore.edit { it[Keys.QUICK_TILE_ADDED] = added }.let { }

    suspend fun setBatteryDialogShown(shown: Boolean) =
        context.dataStore.edit { it[Keys.BATTERY_DIALOG] = shown }.let { }
}
