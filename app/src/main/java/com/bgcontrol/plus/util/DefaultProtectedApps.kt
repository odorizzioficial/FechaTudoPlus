package com.bgcontrol.plus.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.bgcontrol.plus.model.InstalledApp

/**
 * Descobre os aplicativos padrão do aparelho para protegê-los desde a primeira
 * execução. São resolvidos pelo papel que exercem, não por uma lista fixa de
 * pacotes: cada fabricante usa nomes diferentes, então o que vale é perguntar
 * ao próprio Android quem é a tela inicial, o discador, o teclado e assim por diante.
 *
 * Nada disso é imposto: o usuário pode desativar ou remover qualquer um deles.
 */
object DefaultProtectedApps {

    fun detect(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val pacotes = LinkedHashSet<String>()

        PackageUtils.getDefaultLauncher(context)?.let { pacotes.add(it) }
        discador(context)?.let { pacotes.add(it) }
        mensagens(context)?.let { pacotes.add(it) }
        teclado(context)?.let { pacotes.add(it) }
        assistente(context)?.let { pacotes.add(it) }
        porIntent(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))?.let { pacotes.add(it) }
        porIntent(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?.let { pacotes.add(it) }

        pacotes.addAll(familiaShizuku(context))
        pacotes.addAll(wallets(context))

        return pacotes
            .filter { it.isNotBlank() && it != "android" && it != context.packageName }
            .mapNotNull { pkg ->
                val nome = rotulo(pm, pkg) ?: return@mapNotNull null
                InstalledApp(packageName = pkg, appName = nome)
            }
    }

    /**
     * Shizuku, Sui, Shizuku Plus e afins: se o app que dá o acesso privilegiado
     * for encerrado, o BG Control perde as próprias funções. Procuramos pelo
     * nome do pacote em vez de fixar uma lista, porque há vários derivados.
     */
    private fun familiaShizuku(context: Context): List<String> = try {
        context.packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .filter { pkg ->
                val p = pkg.lowercase()
                p.contains("shizuku") || p.contains("shevery") ||
                    p == "com.tsng.hidemyapplist" || p.startsWith("rikka.")
            }
    } catch (t: Throwable) {
        emptyList()
    }

    private fun discador(context: Context): String? = try {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecom?.defaultDialerPackage
    } catch (t: Throwable) {
        null
    }

    private fun mensagens(context: Context): String? = try {
        Telephony.Sms.getDefaultSmsPackage(context)
    } catch (t: Throwable) {
        null
    }

    /** O teclado ativo é um serviço; interessa o pacote a que ele pertence. */
    private fun teclado(context: Context): String? = try {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )?.let { ComponentName.unflattenFromString(it)?.packageName ?: it.substringBefore('/') }
    } catch (t: Throwable) {
        null
    }

    private fun assistente(context: Context): String? = try {
        Settings.Secure.getString(context.contentResolver, "assistant")
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
    } catch (t: Throwable) {
        null
    }

    private fun porIntent(context: Context, intent: Intent): String? = try {
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    } catch (t: Throwable) {
        null
    }

    /**
     * Wallets de fabricantes e de bancos comuns: Samsung Wallet, Google Wallet,
     * Garmin Pay, Huawei Pay e similares. Bloquear esses apps pode impedir
     * pagamentos por NFC mesmo com o celular na tela de bloqueio.
     */
    private val WALLET_PACKAGES = setOf(
        "com.samsung.android.spay",         // Samsung Wallet
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.garmin.android.apps.connectmobile", // Garmin Pay
        "com.huawei.wallet",                // Huawei Wallet
        "com.xiaomi.payment",               // Xiaomi Pay
        "com.bbpos.wiseasy",                // Wise Pay
        "com.squareup.cash",                // Cash App
        "com.paypal.android.p2pmobile",     // PayPal
        "br.com.bradesco.next",             // Next
        "com.nu.production",                // Nubank
        "br.com.intermedium",               // PicPay
        "com.mercadopago.wallet",           // Mercado Pago
        "br.com.stone.production",          // Stone
    )

    private fun wallets(context: Context): List<String> {
        val pm = context.packageManager
        return WALLET_PACKAGES.filter { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (t: Throwable) {
                false
            }
        }
    }

    private fun rotulo(pm: PackageManager, pkg: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        null
    }
}
