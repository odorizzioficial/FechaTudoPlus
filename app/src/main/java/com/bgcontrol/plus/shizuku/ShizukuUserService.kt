package com.bgcontrol.plus.shizuku

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Executado pelo Shizuku dentro de um processo com UID shell (2000).
 * É esse processo que consegue ler a lista real de processos e executar
 * "am force-stop" — o aplicativo em si não tem esse poder.
 */
class ShizukuUserService : IUserService.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused")
    constructor(@Suppress("UNUSED_PARAMETER") context: Context?) : super()

    override fun destroy() {
        // Encerra o processo shell criado pelo Shizuku quando o serviço é desvinculado.
        exitProcess(0)
    }

    override fun execute(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exit = process.waitFor()
            "$exit\n$output"
        } catch (t: Throwable) {
            "-1\n${t.message ?: "erro desconhecido"}"
        }
    }
}
