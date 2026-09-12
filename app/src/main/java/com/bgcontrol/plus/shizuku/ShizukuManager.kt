package com.bgcontrol.plus.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Estado real do Shizuku. Nunca é reportado como ativo sem acesso confirmado. */
enum class ShizukuState {
    /** O aplicativo Shizuku não está instalado no dispositivo. */
    NOT_INSTALLED,

    /** Instalado, mas o serviço não está em execução (precisa ser iniciado no app Shizuku). */
    NOT_RUNNING,

    /** Serviço em execução, mas este aplicativo ainda não recebeu autorização. */
    PERMISSION_REQUIRED,

    /** Serviço em execução e autorização concedida — funções privilegiadas disponíveis. */
    READY
}

data class ShellResult(val exitCode: Int, val output: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

private const val TAG = "ShizukuManager"
/**
 * Gerenciadores conhecidos que implementam a API do Shizuku. O Shevery, por
 * exemplo, trocou o nome do pacote e exige desinstalar o Shizuku oficial, então
 * procurar só pelo pacote original diria "não instalado" com ele funcionando.
 */
private val SHIZUKU_PACKAGES = listOf(
    "moe.shizuku.privileged.api",
    "com.hamondev.shevery"
)

/** Forks e derivados são reconhecidos pelo nome do pacote. */
private val SHIZUKU_PATTERN = Regex("shizuku|shevery|shizuku_plus", RegexOption.IGNORE_CASE)

private const val PERMISSION_REQUEST_CODE = 4471

/**
 * Encapsula todo o acesso ao Shizuku. Mantém o estado observável e expõe
 * [exec] para comandos privilegiados. Se o Shizuku não estiver disponível,
 * [exec] devolve null — quem chama precisa tratar a limitação, não simular sucesso.
 */
class ShizukuManager(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var userService: IUserService? = null
    private var binding = false
    private var pendingBind: CompletableDeferred<IUserService?>? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(false)
        .version(1)

    /** Quantas tentativas de reconexão já fizemos nesta sessão. */
    private var tentativasReconexao = 0

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding = false
            userService = service?.let { IUserService.Stub.asInterface(it) }
            pendingBind?.complete(userService)
            pendingBind = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = false
            userService = null
            // Reconecta automaticamente até 3 vezes antes de desistir.
            // Isso resolve o caso em que o app volta do segundo plano e o
            // Shizuku ainda está vivo mas o bind foi descartado.
            if (tentativasReconexao < 3 && isReady) {
                tentativasReconexao++
                try {
                    Shizuku.bindUserService(userServiceArgs, this)
                } catch (t: Throwable) {
                    Log.w(TAG, "Falha na reconexão automática (\$tentativasReconexao)", t)
                }
            }
            userService = null
            pendingBind?.complete(null)
            pendingBind = null
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        tentativasReconexao = 0   // binder novo = conexão fresca, zera tentativas
        refresh()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        refresh()
    }
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun register() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    fun unregister() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    /** Pacote do gerenciador instalado, seja o oficial, um fork ou um derivado. */
    fun installedManagerPackage(): String? {
        SHIZUKU_PACKAGES.forEach { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }
                .onSuccess { return pkg }
        }
        return runCatching {
            context.packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .firstOrNull { SHIZUKU_PATTERN.containsMatchIn(it) }
        }.getOrNull()
    }

    fun isShizukuInstalled(): Boolean = installedManagerPackage() != null

    fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): ShizukuState {
        // O binder é a única prova que importa: se ele responde, existe um
        // serviço no ar — Shizuku oficial, Shevery, Shizuku+ ou o Sui, que nem
        // aparece como aplicativo por ser módulo do Magisk. Só quando não há
        // resposta é que olhamos se algum gerenciador está instalado, para
        // saber se pedimos para instalar ou apenas para iniciar.
        val alive = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
        if (!alive) {
            return if (isShizukuInstalled()) {
                ShizukuState.NOT_RUNNING
            } else {
                ShizukuState.NOT_INSTALLED
            }
        }
        return try {
            if (Shizuku.isPreV11()) {
                ShizukuState.PERMISSION_REQUIRED
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.READY
            } else {
                ShizukuState.PERMISSION_REQUIRED
            }
        } catch (t: Throwable) {
            ShizukuState.NOT_RUNNING
        }
    }

    val isReady: Boolean get() = _state.value == ShizukuState.READY

    /** Abre o pedido de autorização do próprio Shizuku. Nada é concedido silenciosamente. */
    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            refresh()
            return
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao solicitar permissão do Shizuku", t)
        }
        refresh()
    }

    private suspend fun ensureService(): IUserService? {
        userService?.let { return it }
        if (!isReady) return null
        val deferred = pendingBind ?: CompletableDeferred<IUserService?>().also {
            pendingBind = it
            binding = true
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao vincular o serviço do Shizuku", t)
                binding = false
                pendingBind = null
                it.complete(null)
            }
        }
        return withTimeoutOrNull(8_000) { deferred.await() }
    }

    /**
     * Executa um comando com privilégio de shell.
     * Devolve null quando o Shizuku não está disponível — nunca finge sucesso.
     */
    suspend fun exec(command: String): ShellResult? = withContext(Dispatchers.IO) {
        val service = ensureService() ?: return@withContext null
        try {
            val raw = service.execute(command) ?: return@withContext null
            val separator = raw.indexOf('\n')
            if (separator < 0) return@withContext ShellResult(-1, raw)
            val code = raw.substring(0, separator).trim().toIntOrNull() ?: -1
            ShellResult(code, raw.substring(separator + 1))
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao executar comando", t)
            userService = null
            null
        }
    }

    /** Encerra um aplicativo de fato (equivalente a "adb shell am force-stop"). */
    suspend fun forceStop(packageName: String): Boolean {
        val result = exec("am force-stop $packageName") ?: return false
        return result.isSuccess
    }

    fun unbind() {
        if (userService != null || binding) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
            userService = null
            binding = false
        }
    }
}
