package com.bgcontrol.plus.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.os.Process
import com.bgcontrol.plus.model.InstalledApp

object PackageUtils {

    /**
     * Lista de aplicativos para o seletor.
     *
     * Com [includeSystem], entram também os apps do sistema que têm tela própria
     * (telefone, câmera, relógio e afins). Continuam de fora os componentes sem
     * interface, que não fazem sentido nas listas.
     */
    fun getApps(
        context: Context,
        includeSystem: Boolean,
        requireLauncher: Boolean = true,
        excludePersistent: Boolean = false
    ): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = getDefaultLauncher(context)
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { includeSystem || isUserApp(it) }
            .filter { it.packageName != context.packageName }
            // No seletor só interessam apps com tela própria; na lista de
            // execução não, senão apps de usuário sem ícone na gaveta sumiriam.
            .filter { !requireLauncher || pm.getLaunchIntentForPackage(it.packageName) != null }
            // Processos persistentes do sistema (telefonia, interface) não podem
            // ser encerrados: mostrá-los seria oferecer um botão que não funciona.
            .filter {
                !excludePersistent || it.flags and ApplicationInfo.FLAG_PERSISTENT == 0
            }
            .map {
                InstalledApp(
                    packageName = it.packageName,
                    appName = pm.getApplicationLabel(it).toString(),
                    isLauncher = it.packageName == launcher,
                    isSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    private fun isUserApp(info: ApplicationInfo): Boolean {
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return !system && !updatedSystem
    }

    /**
     * Aplicativos que fazem sentido listar e encerrar.
     *
     * Entram os instalados pelo usuário e também os que vieram de fábrica mas
     * têm tela própria — YouTube, Chrome, os apps da Samsung e afins. Eles são
     * encerrados normalmente e o usuário os reconhece como "seus aplicativos".
     *
     * Ficam de fora os componentes de sistema sem interface (serviços que o
     * Android religa em seguida, dando a impressão de que nada funcionou) e os
     * marcados como persistentes, que não podem ser encerrados.
     */
    /**
     * Determina se um app aparece na lista de Em execução (sem toggle de sistema).
     *
     * Regras (sem toggle ativo):
     *  - FLAG_PERSISTENT = nunca pode ser encerrado → fora
     *  - Apps do usuário → dentro
     *  - Apps de sistema com launcher (YouTube, Chrome, Samsung Pay…) → dentro
     *  - Apps de sistema sem launcher: só entram se forem seguros de fechar —
     *    identificados por terem uma "categoria" real atribuída pela loja
     *    (ApplicationInfo.category), o que na prática só existe em apps de
     *    consumo (Google Play Services, Play Store, apps de fabricante), nunca
     *    em componentes internos do Android (SystemUI, drivers, HALs). Sem essa
     *    checagem, ligar a lista sem o toggle mostrava dezenas de serviços do
     *    núcleo do sistema que o usuário nunca reconheceria nem deveria mexer.
     *  - Um pequeno complemento cobre pacotes muito comuns que às vezes não têm
     *    categoria definida mas são universalmente seguros de encerrar/bloquear.
     */
    private val PACOTES_SISTEMA_SEGUROS = setOf(
        "com.google.android.gms",              // Google Play Services
        "com.google.android.gsf",              // Google Services Framework
        "com.android.vending"                  // Google Play Store
    )

    private fun isKillableApp(pm: PackageManager, info: ApplicationInfo): Boolean {
        if (info.flags and ApplicationInfo.FLAG_PERSISTENT != 0) return false
        if (isUserApp(info)) return true
        if (pm.getLaunchIntentForPackage(info.packageName) != null) return true
        if (info.uid < 10_000) return false
        if (info.packageName in PACOTES_SISTEMA_SEGUROS) return true
        // category != CATEGORY_UNDEFINED é o sinal mais confiável de que a
        // Play Store classificou este pacote como um app de consumo real, e
        // não um componente interno do sistema.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info.category != ApplicationInfo.CATEGORY_UNDEFINED
        } else {
            false
        }
    }

    /** Lista usada pela aba Em execução e pelo seletor de aplicativos. */
    fun getKillableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = getDefaultLauncher(context)
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { isKillableApp(pm, it) }
            .filter { it.packageName != context.packageName }
            .map {
                InstalledApp(
                    packageName = it.packageName,
                    appName = pm.getApplicationLabel(it).toString(),
                    isLauncher = it.packageName == launcher,
                    isSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    /** Diz se o pacote faz parte do sistema (inclui os do sistema atualizados). */
    fun isSystemPackage(context: Context, packageName: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun getDefaultLauncher(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val pkg = resolved?.activityInfo?.packageName
        return if (pkg == null || pkg == "android") null else pkg
    }

    fun getAppLabel(context: Context, packageName: String): String? = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun appVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: PackageManager.NameNotFoundException) {
        "1.0.0"
    }

    /** Permissão de acesso ao uso: concedida manualmente pelo usuário nas configurações. */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
