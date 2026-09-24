package com.bgcontrol.plus.monitor

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.bgcontrol.plus.model.InstalledApp
import com.bgcontrol.plus.model.MonitorSource
import com.bgcontrol.plus.model.RunningAppInfo
import com.bgcontrol.plus.shizuku.ShizukuManager
import com.bgcontrol.plus.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class RunningSnapshot(
    val apps: List<RunningAppInfo>,
    val source: MonitorSource
)

/**
 * Leitura do estado real do sistema.
 *
 * Com Shizuku: lê a tabela de processos (ps/top), o que dá memória residente e CPU
 * reais por pacote.
 * Sem Shizuku: o Android não permite enxergar processos de terceiros desde o
 * Android 8, então só é possível listar os apps usados recentemente, sem métricas.
 * Nesse caso os campos de memória e CPU ficam nulos e a interface mostra "—".
 */
class AppMonitor(
    private val context: Context,
    private val shizuku: ShizukuManager
) {

    /**
     * Lista o que está em execução.
     *
     * Com [includeSystem] = false já aparecem apps de sistema que têm processos
     * relevantes (com UID de app, não de sistema raiz). Com [includeSystem] = true
     * aparecem todos, incluindo serviços do núcleo do Android.
     */
    suspend fun snapshot(includeSystem: Boolean = false): RunningSnapshot =
        withContext(Dispatchers.IO) {
        val userApps = installedAppsCache(includeSystem)
        val foreground = currentForegroundPackage()

        if (shizuku.isReady) {
            val processes = readProcessTable()
            if (processes.isNotEmpty()) {
                val apps = processes
                    .filterKeys { userApps.containsKey(it) }
                    .map { (pkg, entries) ->
                        RunningAppInfo(
                            packageName = pkg,
                            appName = userApps.getValue(pkg).appName,
                            pids = entries.map { it.pid },
                            memoryBytes = entries.sumOf { it.rssBytes }.takeIf { it > 0 },
                            isForeground = pkg == foreground,
                            hasBackgroundProcess = true
                        )
                    }
                    .sortedByDescending { it.memoryBytes ?: 0L }
                return@withContext RunningSnapshot(apps, MonitorSource.SHIZUKU)
            }
        }

        if (PackageUtils.hasUsageStatsPermission(context)) {
            val recent = recentlyUsedPackages()
                .filter { userApps.containsKey(it) }
                .map { pkg ->
                    RunningAppInfo(
                        packageName = pkg,
                        appName = userApps.getValue(pkg).appName,
                        memoryBytes = null,
                        isForeground = pkg == foreground,
                        hasBackgroundProcess = false
                    )
                }
            return@withContext RunningSnapshot(recent, MonitorSource.USAGE_STATS_LIMITED)
        }

        RunningSnapshot(emptyList(), MonitorSource.UNAVAILABLE)
    }

    /**
     * Pacotes da aba Bloqueados que devem continuar suspensos.
     *
     * O encerramento normal suspende e libera o pacote na sequência, só para
     * limpar os recentes. Para um app bloqueado essa liberação desfazia o
     * bloqueio poucos milissegundos depois de aplicá-lo — era por isso que o
     * Play Services e outros teimosos voltavam para a lista. Este conjunto é
     * mantido em dia por [BgControlApp] e faz todo comando pular o unsuspend.
     */
    @Volatile
    var pacotesBloqueados: Set<String> = emptySet()

    @Volatile
    private var appsEmCache: Map<String, InstalledApp>? = null
    private var appsEmCacheEm = 0L
    private var cacheComSistema = false

    private data class ProcessEntry(val pid: Int, val rssBytes: Long)

    /**
     * Tabela de processos lida com UID shell via Shizuku.
     *
     * A coluna usada é CMDLINE, não NAME: o NAME do toybox vem do `comm` do
     * kernel, que o Linux corta em 15 caracteres. Com ele, qualquer pacote de
     * nome mais longo — a maioria — chegava truncado e não casava com nenhum
     * aplicativo instalado, o que escondia boa parte da lista.
     */
    private suspend fun readProcessTable(): Map<String, List<ProcessEntry>> {
        val result = shizuku.exec("ps -A -o PID,RSS,CMDLINE")
            ?: return emptyMap()
        if (!result.isSuccess || result.output.lineSequence().count() < 2) {
            return readProcessTableFallback()
        }
        val map = mutableMapOf<String, MutableList<ProcessEntry>>()
        result.output.lineSequence()
            .drop(1)
            .forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) return@forEach
                val pid = parts[0].toIntOrNull() ?: return@forEach
                val rssKb = parts[1].removeSuffix("K").toLongOrNull() ?: return@forEach
                val processName = parts[2]
                // Processos secundários usam "pacote:sufixo"; agrupamos pelo pacote.
                val pkg = processName.substringBefore(':')
                if (!pkg.contains('.')) return@forEach
                map.getOrPut(pkg) { mutableListOf() }
                    .add(ProcessEntry(pid, rssKb * 1024))
            }
        return map
    }

    /** Alguns aparelhos não aceitam CMDLINE; nesses, o NAME truncado é o que há. */
    private suspend fun readProcessTableFallback(): Map<String, List<ProcessEntry>> {
        val result = shizuku.exec("ps -A -o PID,RSS,NAME") ?: return emptyMap()
        if (!result.isSuccess) return emptyMap()
        val map = mutableMapOf<String, MutableList<ProcessEntry>>()
        result.output.lineSequence()
            .drop(1)
            .forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) return@forEach
                val pid = parts[0].toIntOrNull() ?: return@forEach
                val rssKb = parts[1].removeSuffix("K").toLongOrNull() ?: return@forEach
                val pkg = parts[2].substringBefore(':')
                if (!pkg.contains('.')) return@forEach
                map.getOrPut(pkg) { mutableListOf() }.add(ProcessEntry(pid, rssKb * 1024))
            }
        return map
    }

    /**
     * Descobre qual app está em primeiro plano agora.
     *
     * BUG CRÍTICO CORRIGIDO: a janela de consulta era de apenas 60 segundos.
     * Eventos de uso só disparam na troca de app — se o usuário ficasse mais
     * de 60 segundos dentro do mesmo aplicativo sem trocar de tela, o evento
     * de "entrou em primeiro plano" saía da janela consultada, a função
     * passava a devolver null, e o vigia de bloqueados concluía (errado) que
     * o app tinha saído de primeiro plano — encerrando um app bloqueado
     * mesmo com o usuário usando ele naquele instante. A janela agora cobre
     * as últimas 24 horas: sempre encontra a transição mais recente de
     * verdade, não importa há quanto tempo o usuário está no mesmo app.
     */
    fun currentForegroundPackage(): String? {
        if (!PackageUtils.hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - JANELA_PRIMEIRO_PLANO_MS, now)
        var last: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = event.packageName
            }
        }
        return last
    }

    /**
     * Mesma pergunta que [currentForegroundPackage], mas lida direto do
     * gerenciador de janelas via shell, sem passar pelo banco de estatísticas
     * de uso do Android.
     *
     * Em alguns aparelhos Samsung (relatado em Android 13/One UI 5.1), esse
     * banco só é gravado a cada dezenas de segundos por conta de otimizações
     * de bateria da própria fabricante — usado como reserva; a fonte
     * principal é o histórico de uso do Android em [currentForegroundPackage].
     */
    private suspend fun currentForegroundPackageViaShell(): String? {
        if (!shizuku.isReady) return null
        val resultado = shizuku.exec("dumpsys window | grep -m1 mCurrentFocus") ?: return null
        return FOCO_JANELA.find(resultado.output)?.groupValues?.get(1)
    }

    /**
     * Descobre o app em primeiro plano preferindo o histórico de uso do
     * Android e caindo para a leitura em tempo real via shell só quando a
     * permissão de acesso de uso não está concedida ou a consulta falha.
     */
    /**
     * Descobre o app em primeiro plano preferindo a leitura em tempo real via
     * shell, com o histórico de uso do Android como reserva.
     *
     * Em algumas versões mais antigas do One UI (relatado em Android 13/One
     * UI 5.1), o histórico de uso pode demorar a gravar — mas ele nunca
     * volta vazio, só desatualizado, e como sempre responde alguma coisa
     * (mesmo que errada), usá-lo como primeira fonte fazia a leitura em
     * tempo real via shell nunca ser sequer tentada como conferência. A
     * leitura em tempo real não depende de nenhum banco gravado com atraso,
     * então é a fonte mais confiável quando o Shizuku está disponível — o
     * histórico de uso fica só como reserva para quando ele não está.
     */
    suspend fun currentForegroundPackageRapido(): String? =
        currentForegroundPackageViaShell() ?: currentForegroundPackage()

    private fun recentlyUsedPackages(): List<String> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30 * 60_000L, now)
        val packages = LinkedHashSet<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                packages.add(event.packageName)
            }
        }
        return packages.toList().reversed()
    }

    /** Memória residente atual de um pacote, quando mensurável. */
    suspend fun memoryOf(packageName: String): Long? {
        if (!shizuku.isReady) return null
        return readProcessTable()[packageName]?.sumOf { it.rssBytes }?.takeIf { it > 0 }
    }

    /**
     * Encerra um aplicativo de verdade, com verificação.
     *
     * Alguns aplicativos (o "Ligar ao Windows" é o caso clássico) voltam sozinhos
     * logo depois do force-stop, porque o sistema os religa. Por isso a operação
     * é escalonada: encerra, confere na tabela de processos e, se ele voltou,
     * mata os processos em segundo plano e encerra de novo.
     */
    /** Exposto para ViewModels que precisam verificar o estado do Shizuku. */
    fun shizukuReady(): Boolean = shizuku.isReady

    /** Executa um comando privilegiado arbitrário via Shizuku. */
    suspend fun execPrivileged(cmd: String) = shizuku.exec(cmd)

    /**
     * Concede de uma vez, via Shizuku, as permissões que normalmente exigem
     * navegar até uma tela do Android e tocar em conceder manualmente.
     *
     * Só é possível porque o Shizuku já dá o mesmo nível de acesso que o
     * comando `adb shell` — e é exatamente isso que o `adb shell pm grant` e
     * o `adb shell appops set` fazem manualmente quando alguém mexe no
     * celular pelo computador. Aqui é o mesmo poder, só que através do
     * Shizuku em vez de um cabo USB.
     *
     *  - Notificações: uma permissão de execução normal, concedida com `pm
     *    grant`.
     *  - Sobrepor outros apps e Acesso de uso: não são permissões comuns,
     *    são liberações de "appops" — o mesmo mecanismo usado pelo Android
     *    para controlar esse tipo de acesso especial. `appops set` também
     *    funciona com privilégio de shell.
     *  - Bateria: adiciona o próprio pacote à lista de exceções de
     *    otimização de bateria do sistema (`dumpsys deviceidle whitelist`).
     *
     * O Shizuku em si continua exigindo a autorização manual de sempre —
     * não tem como pular essa primeira etapa, é o próprio mecanismo que dá
     * acesso a tudo o resto.
     */
    suspend fun concederPermissoesEssenciais(pacote: String): Boolean {
        if (!shizuku.isReady) return false
        val comandos = listOf(
            "pm grant $pacote android.permission.POST_NOTIFICATIONS",
            "appops set $pacote SYSTEM_ALERT_WINDOW allow",
            "appops set $pacote GET_USAGE_STATS allow",
            "dumpsys deviceidle whitelist +$pacote"
        )
        shizuku.exec(comandos.joinToString(" 2>/dev/null; ") + " 2>/dev/null")
        return true
    }

    suspend fun stopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!VALID_PACKAGE.matches(packageName)) return@withContext false

        if (shizuku.isReady) {
            shizuku.exec(comandoEncerrar(packageName))
            delay(VERIFICACAO_MS)
            if (memoryOf(packageName) == null) {
                garantirLiberados(listOf(packageName))
                return@withContext true
            }

            // Voltou: derruba os processos em segundo plano e repete o encerramento.
            shizuku.exec("am kill $packageName")
            shizuku.exec(comandoEncerrar(packageName))
            delay(VERIFICACAO_MS)
            garantirLiberados(listOf(packageName))
            return@withContext memoryOf(packageName) == null
        }

        return@withContext try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            // Sem Shizuku o Android não confirma o encerramento nem deixa verificar.
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Encerra vários aplicativos de uma vez.
     *
     * Todos os comandos são disparados em paralelo dentro de um único shell:
     * uma ida e volta de IPC em vez de uma por aplicativo, o que torna o
     * "Fechar Tudo" praticamente instantâneo. Quem sobreviver leva uma segunda
     * rodada, e o retorno é a contagem real de encerrados.
     */
    suspend fun stopApps(packages: List<String>): Int = withContext(Dispatchers.IO) {
        val alvos = packages.filter { VALID_PACKAGE.matches(it) }
        if (alvos.isEmpty()) return@withContext 0

        if (shizuku.isReady) {
            // Cada alvo roda em um subshell próprio, todos em paralelo.
            val comando = alvos.joinToString(" & ") { "( ${comandoEncerrar(it)} )" } + " & wait"
            shizuku.exec(comando)
            delay(VERIFICACAO_MS)

            // Quem sobreviveu leva a segunda rodada, mais insistente.
            val vivos = readProcessTable().keys.filter { it in alvos }
            if (vivos.isEmpty()) {
                garantirLiberados(alvos)
                return@withContext alvos.size
            }

            val segunda = vivos.joinToString(" & ") {
                "( am kill $it; ${comandoEncerrar(it)} )"
            } + " & wait"
            shizuku.exec(segunda)
            delay(VERIFICACAO_MS)
            garantirLiberados(alvos)
            val aindaVivos = readProcessTable().keys.count { it in alvos }
            return@withContext alvos.size - aindaVivos
        }

        // Sem Shizuku: dispara os pedidos em paralelo, dentro do que o Android permite.
        coroutineScope {
            alvos.map { pkg -> async { if (stopApp(pkg)) 1 else 0 } }.awaitAll().sum()
        }
    }

    /**
     * Segunda garantia de que nenhum pacote ficou suspenso por acidente.
     *
     * Bloqueio não usa mais suspensão permanente (ver [comandoEncerrar]) —
     * qualquer pacote passado aqui deve simplesmente ser liberado, bloqueado
     * ou não. Uma versão anterior pulava os bloqueados de propósito, quando a
     * suspensão ainda era o mecanismo de bloqueio; manter esse pulo agora
     * arriscava deixar um app bloqueado preso suspenso por acidente num
     * retry, repetindo o mesmo bug que já foi corrigido.
     */
    private suspend fun garantirLiberados(packages: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || packages.isEmpty()) return
        shizuku.exec(packages.joinToString("; ") { "pm unsuspend --user 0 $it" })
    }

    /**
     * Monta o comando que realmente encerra um app e limpa o cartão dele dos
     * recentes.
     *
     * Sempre manda o usuário para a tela inicial antes do force-stop quando o
     * alvo está em primeiro plano — sem isso, encerrar um app que está aberto
     * na tela naquele instante (por exemplo, ao tocar a bolha mágica com a
     * Play Store em primeiro plano) não limpava os recentes: suspender um app
     * que ainda está em foco não finaliza a task da mesma forma que suspender
     * um app em segundo plano.
     */
    private fun comandoEncerrar(packageName: String): String {
        // Só manda para a tela inicial quando o próprio pacote-alvo é o que
        // está em primeiro plano. Sem essa checagem, encerrar qualquer app a
        // partir de dentro do Fecha Tudo Plus levava o próprio app para
        // segundo plano — o usuário via a tela inicial e achava que o app
        // tinha fechado sozinho, mesmo o alvo sendo outro pacote qualquer.
        val alvoEmPrimeiroPlano = packageName == currentForegroundPackage()
        val irParaHome = if (alvoEmPrimeiroPlano) {
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME; "
        } else {
            ""
        }
        val encerrar = "am force-stop $packageName"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "$irParaHome$encerrar"

        // Ordem: force-stop, depois suspender/liberar — sem repetir o
        // force-stop no final.
        //
        // Chegou a existir uma versão que repetia o force-stop depois de
        // suspender/liberar, só para deixar o botão "Forçar parada" cinza na
        // tela de Informações do app (puramente cosmético). Essa mudança
        // causou falhas intermitentes reais em aparelho de verdade — às
        // vezes o app simplesmente não era encerrado. A comunicação extra
        // com o Shizuku, uma chamada a mais em sequência, parece introduzir
        // uma corrida que não existe com a sequência mais simples. Como
        // encerrar o app de fato é muito mais importante do que o botão
        // aparecer cinza, a sequência simples e comprovadamente confiável
        // fica assim, sem o force-stop repetido.
        val suspenderLiberar =
            "pm suspend --user 0 $packageName; pm unsuspend --user 0 $packageName"

        // Bloqueados usam o mesmo comando leve dos apps normais abaixo:
        // suspender e liberar, sempre — nunca desativar/reativar o pacote.
        //
        // Uma tentativa anterior usava desativar/reativar quando o Fecha
        // Tudo Plus está em segundo plano, pensando em resolver um cartão de
        // recentes preso em certos aparelhos com Android 13/One UI mais
        // antigo. O problema: desativar um pacote também apaga o atalho dele
        // da tela inicial em qualquer launcher — e como o vigia de
        // bloqueados encerra o app toda vez que ele sai do primeiro plano,
        // isso apagava o atalho repetidamente, em qualquer aparelho, não só
        // no que tinha o problema original. Um cartão de recentes preso é
        // cosmético — o processo é encerrado do mesmo jeito, a restrição de
        // segundo plano continua valendo. Perder o atalho da tela inicial
        // toda vez é bem mais grave, então fica assim: sempre o caminho leve.
        if (packageName in pacotesBloqueados) {
            return "$irParaHome$encerrar; $suspenderLiberar"
        }

        // Apps normais: mesma sequência.
        return "$irParaHome$encerrar; $suspenderLiberar"
    }

    /**
     * Bloqueio real, sem travar a abertura manual.
     *
     * O force-stop sozinho nunca segurou apps como o Google Play Services: o
     * sistema os religa em segundos por causa de alarmes, jobs e broadcasts
     * pendentes. As camadas abaixo tiram cada um desses caminhos de volta,
     * sem usar `pm suspend` — suspender impede até o toque manual do usuário
     * no ícone (mostra "app não disponível, gerenciado pelo app Shell"), o
     * que não é bloqueio de segundo plano, é desativar o app por completo:
     *
     *  1. corta as permissões de rodar em segundo plano (appops);
     *  2. joga o app no balde "restricted", sem jobs nem alarmes livres;
     *  3. encerra o que ainda estiver de pé.
     *
     * Se o app voltar sozinho mesmo assim, é o [BlockedAppWatcherService] que
     * detecta e encerra de novo — o usuário sempre pode abrir manualmente,
     * só não consegue deixá-lo rodando quando sai dele.
     *
     * Tudo é reversível e volta ao normal em [removerBloqueio]. Sem Shizuku
     * nada disso está disponível: o Android não concede esse poder a um app
     * comum, e a função apenas retorna false.
     */
    suspend fun aplicarBloqueio(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!shizuku.isReady || !VALID_PACKAGE.matches(packageName)) return@withContext false

        val comandos = listOf(
            "cmd appops set $packageName RUN_IN_BACKGROUND ignore",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore",
            "am set-standby-bucket $packageName restricted",
            "am force-stop $packageName"
        )
        shizuku.exec(comandos.joinToString("; "))
        true
    }

    /** Desfaz [aplicarBloqueio] por inteiro — o app volta ao estado do sistema. */
    suspend fun removerBloqueio(packageName: String) = withContext(Dispatchers.IO) {
        if (!shizuku.isReady || !VALID_PACKAGE.matches(packageName)) return@withContext

        val comandos = buildList {
            // Rede de segurança: versões antigas deste app chegaram a usar
            // pm suspend para bloquear. Isto desfaz qualquer suspensão que
            // ainda esteja pendente de uma instalação anterior.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add("pm unsuspend --user 0 $packageName 2>/dev/null || true")
            }
            add("cmd appops set $packageName RUN_IN_BACKGROUND allow")
            add("cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow")
            add("am set-standby-bucket $packageName active")
        }
        shizuku.exec(comandos.joinToString("; "))
    }

    /**
     * Reaplica o bloqueio de todos os pacotes de uma vez.
     *
     * Atualizações pela Play Store, troca de usuário e algumas ROMs desfazem
     * as restrições de appops/standby por conta própria, e não existe comando
     * confiável em todas as versões para perguntar "este pacote ainda está
     * restrito?". Reaplicar é idempotente e custa uma única ida ao shell,
     * então o vigia simplesmente repete o comando de tempos em tempos.
     */
    suspend fun reforcarBloqueios(packages: Collection<String>) = withContext(Dispatchers.IO) {
        if (!shizuku.isReady) return@withContext
        val alvos = packages.filter { VALID_PACKAGE.matches(it) }
        if (alvos.isEmpty()) return@withContext
        val comandos = alvos.flatMap { pkg ->
            listOf(
                "cmd appops set $pkg RUN_IN_BACKGROUND ignore",
                "cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore",
                "am set-standby-bucket $pkg restricted"
            )
        }
        shizuku.exec(comandos.joinToString("; "))
    }

    /**
     * Restrição profunda de segundo plano — o mesmo que o Android aplica em
     * "Bateria > Restrito". É isto que impede o aplicativo de se religar sozinho
     * depois de encerrado; sem isso, apps teimosos voltam em segundos.
     *
     * Tudo é revertido quando o usuário desbloqueia.
     */
    suspend fun setBackgroundRestricted(packageName: String, restricted: Boolean) {
        if (!shizuku.isReady || !VALID_PACKAGE.matches(packageName)) return
        val modo = if (restricted) "ignore" else "allow"
        val bucket = if (restricted) "restricted" else "active"
        shizuku.exec(
            "cmd appops set $packageName RUN_IN_BACKGROUND $modo; " +
                "cmd appops set $packageName RUN_ANY_IN_BACKGROUND $modo; " +
                "am set-standby-bucket $packageName $bucket"
        )
    }

    /**
     * Lista do seletor.
     *
     * Sem o botão ligado, só os aplicativos instalados pelo usuário — é o que
     * ele espera ver ao tocar em "Adicionar". Com o botão, entra tudo que está
     * instalado, inclusive componentes de fábrica sem tela própria.
     */
    fun allApps(includeSystem: Boolean): List<InstalledApp> =
        PackageUtils.getApps(
            context,
            includeSystem = includeSystem,
            requireLauncher = !includeSystem
        )

    /**
     * Mapa de aplicativos instalados, guardado por um minuto.
     *
     * Montar essa lista carrega o rótulo de cada pacote pelo PackageManager, o
     * que custa caro; sem o cache isso rodaria a cada 2,5 segundos com a aba
     * aberta. Aplicativos instalados ou removidos aparecem no ciclo seguinte.
     */
    private fun installedAppsCache(includeSystem: Boolean): Map<String, InstalledApp> {
        val agora = System.currentTimeMillis()
        val atual = appsEmCache
        if (atual != null && agora - appsEmCacheEm < CACHE_APPS_MS && cacheComSistema == includeSystem) {
            return atual
        }

        // Apps do usuário mais os de fábrica que foram atualizados pela loja:
        // todos encerráveis de verdade. Ver PackageUtils.isKillableApp.
        val novo = if (includeSystem) {
            PackageUtils.getApps(context, includeSystem = true, requireLauncher = false)
        } else {
            PackageUtils.getKillableApps(context)
        }.associateBy { it.packageName }
        appsEmCache = novo
        appsEmCacheEm = agora
        cacheComSistema = includeSystem
        return novo
    }

    /** Descarta o cache: usado quando a lista precisa refletir uma mudança agora. */
    fun invalidateAppsCache() {
        appsEmCache = null
    }

    private companion object {
        /** Só nomes de pacote válidos entram na linha de comando. */
        val VALID_PACKAGE = Regex("^[A-Za-z0-9._]+$")

        /** Janela de consulta para descobrir o app em primeiro plano. Ver [currentForegroundPackage]. */
        const val JANELA_PRIMEIRO_PLANO_MS = 24 * 60 * 60 * 1_000L

        /**
         * Extrai o nome do pacote da linha `mCurrentFocus` do `dumpsys window`.
         * Formato típico: `mCurrentFocus=Window{... u0 com.exemplo/com.exemplo.MainActivity}`.
         * Esse padrão (usuário seguido do pacote antes da barra) é estável
         * entre praticamente todas as versões do Android, incluindo as
         * customizações de fabricante — é a mesma técnica usada por
         * ferramentas de automação de testes há anos.
         */
        val FOCO_JANELA = Regex("""u\d+\s+([a-zA-Z0-9_.]+)/""")

        /** Tempo entre encerrar e conferir se o processo realmente sumiu. */
        const val VERIFICACAO_MS = 350L

        /** Validade do mapa de aplicativos instalados. */
        const val CACHE_APPS_MS = 60_000L
    }
}
