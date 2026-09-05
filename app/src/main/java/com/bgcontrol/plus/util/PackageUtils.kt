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

    /** Somente aplicativos instalados pelo usuário — processos e apps do sistema ficam de fora. */
    fun getUserApps(context: Context): List<InstalledApp> = getApps(context, includeSystem = false)

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
     * Aplicativos que o "Fechar Tudo" encerra e que ficam encerrados.
     *
     * Só os instalados pelo usuário. Apps que vieram de fábrica ficam de fora
     * mesmo quando atualizados pela loja: o sistema costuma religá-los logo
     * depois do encerramento, e vê-los reaparecendo na lista dá a impressão de
     * que o aplicativo não funcionou. Também ficam de fora os persistentes.
     */
    private fun isKillableApp(info: ApplicationInfo): Boolean {
        val persistent = info.flags and ApplicationInfo.FLAG_PERSISTENT != 0
        return isUserApp(info) && !persistent
    }

    /** Lista usada pela aba Em execução. */
    fun getKillableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = getDefaultLauncher(context)
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { isKillableApp(it) }
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
