package com.bgcontrol.plus.model

/**
 * Estado real de um aplicativo em execução.
 * memoryBytes nulo significa "não mensurável neste dispositivo" — a interface
 * mostra "—" nesses casos e nunca um valor inventado.
 */
data class RunningAppInfo(
    val packageName: String,
    val appName: String,
    val pids: List<Int> = emptyList(),
    val memoryBytes: Long? = null,
    val isForeground: Boolean = false,
    val hasBackgroundProcess: Boolean = true
)

/** Como a lista de "Em execução" foi obtida — determina o que pode ser exibido. */
enum class MonitorSource {
    /** Shizuku autorizado: leitura real de processos e memória. */
    SHIZUKU,
    /** Sem Shizuku: apenas apps usados recentemente, sem métricas de processo. */
    USAGE_STATS_LIMITED,
    /** Sem Shizuku e sem permissão de uso: não há dados confiáveis. */
    UNAVAILABLE
}
