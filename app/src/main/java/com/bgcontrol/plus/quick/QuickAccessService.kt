package com.bgcontrol.plus.quick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.MainActivity
import com.bgcontrol.plus.R
import com.bgcontrol.plus.preferences.AppSettings
import com.bgcontrol.plus.util.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Serviço das ações rápidas: a notificação fixa e a bolha flutuante.
 *
 * Os dois vivem no mesmo serviço porque a bolha precisa de um serviço em
 * primeiro plano para sobreviver, e esse serviço já é a notificação. Manter
 * dois seria duas notificações na barra para o usuário.
 */
class QuickAccessService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Aplica mudanças de preferência assim que elas acontecem. */
    private var observadorPrefs: Job? = null

    /** Verifica de tempos em tempos qual aplicativo está na frente. */
    private var vigiaPrimeiroPlano: Job? = null

    private lateinit var app: BgControlApp
    private var windowManager: WindowManager? = null
    private var bolha: ImageView? = null
    private var params: WindowManager.LayoutParams? = null

    /** Última configuração conhecida, usada pelos dois laços e pelo toque. */
    @Volatile
    private var prefsAtuais: AppSettings = AppSettings()

    /** True quando o app da vez está na lista de "ocultar a bolha em". */
    @Volatile
    private var emAppOculto = false

    private val handlerPrincipal = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        app = application as BgControlApp
        criarCanal()
        iniciarEmPrimeiroPlano()
        observarPreferencias()
        observarPrimeiroPlano()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // O sistema pode recriar o serviço sem passar por onCreate com a
        // notificação ativa; repetir aqui é barato e evita ANR de FGS.
        iniciarEmPrimeiroPlano()
        when (intent?.action) {
            ACTION_FECHAR_TUDO -> fecharTudo()
            ACTION_RESET_POSICAO -> resetarPosicaoDaBolha()
            ACTION_NOTIFICACAO_DESCARTADA -> reapresentarSeAindaAtiva()
        }
        return START_STICKY
    }

    /**
     * Move a bolha já existente na tela de volta ao centro, e grava essa
     * posição. Sem chamar [windowManager]?.updateViewLayout diretamente aqui,
     * o botão de reset só mudava o valor salvo — a bolha continuava fora de
     * alcance até o serviço ser recriado, o que na prática nunca acontecia
     * enquanto o app ficava aberto.
     */
    private fun resetarPosicaoDaBolha() {
        val view = bolha
        val layout = params
        val lado = dpParaPx(prefsAtuais.bubbleSize)
        val dm = resources.displayMetrics
        val centroX = (dm.widthPixels - lado) / 2
        val centroY = (dm.heightPixels - lado) / 2

        if (view != null && layout != null) {
            layout.x = centroX
            layout.y = centroY
            runCatching { windowManager?.updateViewLayout(view, layout) }
        }
        guardarPosicao(centroX, centroY)
    }

    /**
     * Sobe a notificação do serviço.
     *
     * A partir do Android 13 a notificação só aparece se o usuário tiver
     * concedido POST_NOTIFICATIONS. Quando ela não aparecia, era isto: o
     * serviço subia, a notificação era montada e o sistema a descartava em
     * silêncio. Quem pede a permissão é a MainActivity, ao ligar o interruptor.
     */
    private fun iniciarEmPrimeiroPlano() {
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000 (API 34+).
        // Usamos o valor literal para manter compatibilidade com minSdk 26;
        // em versões mais antigas o tipo é ignorado pelo sistema.
        val tipo = if (Build.VERSION.SDK_INT >= 34) 0x40000000 else 0
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICACAO_ID, montarNotificacao(), tipo)
        }
    }

    /**
     * Esconde ou exibe a notificação conforme a preferência do usuário.
     * O serviço precisa estar em primeiro plano para a bolha funcionar, mas
     * isso não obriga a notificação a ser visível — cancelar com IMPORTANCE_MIN
     * faz ela sumir da barra enquanto o serviço continua vivo.
     */
    private fun sincronizarVisibilidadeNotificacao(prefs: AppSettings) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (!prefs.persistentNotification) {
            nm.cancel(NOTIFICACAO_ID)
        } else {
            nm.notify(NOTIFICACAO_ID, montarNotificacao())
        }
    }

    /**
     * Reage a cada mudança de configuração no mesmo instante.
     *
     * Antes isto era um laço que relia as preferências a cada 1,5 s; mexer na
     * opacidade ou no tamanho só surtia efeito no ciclo seguinte. Coletando o
     * Flow, o ajuste aparece na tela enquanto o dedo ainda está no controle.
     */
    private fun observarPreferencias() {
        observadorPrefs?.cancel()
        observadorPrefs = scope.launch {
            app.container.settingsRepository.settings.collect { prefs ->
                prefsAtuais = prefs

                if (!prefs.persistentNotification && !prefs.bubbleEnabled) {
                    withContext(Dispatchers.Main) { removerBolha() }
                    stopSelf()
                    return@collect
                }

                withContext(Dispatchers.Main) {
                    sincronizarVisibilidadeNotificacao(prefs)
                    aplicarEstadoDaBolha()
                }
            }
        }
    }

    /** Só descobre em que app o usuário está; a decisão fica em [aplicarEstadoDaBolha]. */
    private fun observarPrimeiroPlano() {
        vigiaPrimeiroPlano?.cancel()
        vigiaPrimeiroPlano = scope.launch {
            while (isActive) {
                val prefs = prefsAtuais
                if (prefs.bubbleEnabled) {
                    val atual = app.container.appMonitor.currentForegroundPackage()
                    // O próprio app não entra mais nesta conta: a bolha precisa
                    // ficar visível na tela de ajustes para o usuário ver a
                    // opacidade e o tamanho mudarem enquanto arrasta o controle.
                    val oculto = atual != null && atual in prefs.bubbleExcluded
                    if (oculto != emAppOculto) {
                        emAppOculto = oculto
                        withContext(Dispatchers.Main) {
                    sincronizarVisibilidadeNotificacao(prefs)
                    aplicarEstadoDaBolha()
                }
                    }
                }
                delay(if (prefs.bubbleEnabled) INTERVALO_MS else INTERVALO_PARADO_MS)
            }
        }
    }

    /** Mostra, esconde ou só reajusta a bolha conforme o estado do momento. */
    private fun aplicarEstadoDaBolha() {
        val prefs = prefsAtuais
        val podeSobrepor = Settings.canDrawOverlays(this)
        val deveMostrar = prefs.bubbleEnabled && podeSobrepor && !emAppOculto

        if (!deveMostrar) {
            removerBolha()
            return
        }
        if (bolha == null) mostrarBolha() else atualizarAparencia()
    }

    private fun mostrarBolha() {
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .also { windowManager = it }

        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            // Sem isto o desenho não acompanha o tamanho pedido pelo usuário.
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val lado = dpParaPx(prefsAtuais.bubbleSize)
        val layout = WindowManager.LayoutParams(
            lado,
            lado,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Posição 0,0 = primeira abertura; coloca no centro da tela.
            val dm = resources.displayMetrics
            x = if (prefsAtuais.bubbleX == 0 && prefsAtuais.bubbleY == 300) {
                (dm.widthPixels - lado) / 2
            } else {
                prefsAtuais.bubbleX.coerceIn(0, dm.widthPixels - lado)
            }
            y = if (prefsAtuais.bubbleX == 0 && prefsAtuais.bubbleY == 300) {
                (dm.heightPixels - lado) / 2
            } else {
                prefsAtuais.bubbleY.coerceIn(0, dm.heightPixels - lado)
            }
        }

        view.alpha = prefsAtuais.bubbleOpacity / 100f
        view.setOnTouchListener(ouvinteDeToque(view, layout))

        runCatching {
            wm.addView(view, layout)
            bolha = view
            params = layout
        }
    }

    /** Aplica opacidade, tamanho e posição sem recriar a janela. */
    private fun atualizarAparencia() {
        val view = bolha ?: return
        val layout = params ?: return
        val prefs = prefsAtuais

        view.alpha = prefs.bubbleOpacity / 100f
        val lado = dpParaPx(prefs.bubbleSize)
        if (layout.width != lado || layout.height != lado) {
            layout.width = lado
            layout.height = lado
            runCatching { windowManager?.updateViewLayout(view, layout) }
        }
    }

    /**
     * Toque curto encerra tudo; arrastar move e guarda a posição; segurar por
     * três segundos abre as configurações da bolha.
     *
     * O toque longo cancela a limpeza de propósito: quem segura está indo para
     * os ajustes e não quer que nada seja encerrado ao soltar o dedo.
     */
    private fun ouvinteDeToque(
        view: ImageView,
        layout: WindowManager.LayoutParams
    ): View.OnTouchListener {
        var inicioX = 0
        var inicioY = 0
        var toqueX = 0f
        var toqueY = 0f
        var toqueLongoDisparado = false

        val abrirConfiguracoes = Runnable {
            toqueLongoDisparado = true
            abrirConfiguracoesDaBolha()
        }

        return View.OnTouchListener { _, evento ->
            when (evento.action) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = layout.x
                    inicioY = layout.y
                    toqueX = evento.rawX
                    toqueY = evento.rawY
                    toqueLongoDisparado = false
                    handlerPrincipal.postDelayed(abrirConfiguracoes, TOQUE_LONGO_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val andou = abs(evento.rawX - toqueX) > TOLERANCIA_TOQUE ||
                        abs(evento.rawY - toqueY) > TOLERANCIA_TOQUE
                    // Arrastar não é segurar: o atalho é cancelado assim que a
                    // bolha começa a se mover.
                    if (andou) handlerPrincipal.removeCallbacks(abrirConfiguracoes)
                    layout.x = inicioX + (evento.rawX - toqueX).toInt()
                    layout.y = inicioY + (evento.rawY - toqueY).toInt()
                    runCatching { windowManager?.updateViewLayout(view, layout) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handlerPrincipal.removeCallbacks(abrirConfiguracoes)
                    val andou = abs(evento.rawX - toqueX) > TOLERANCIA_TOQUE ||
                        abs(evento.rawY - toqueY) > TOLERANCIA_TOQUE
                    when {
                        andou -> guardarPosicao(layout.x, layout.y)
                        // Soltou depois de segurar: nenhuma limpeza acontece.
                        toqueLongoDisparado -> Unit
                        else -> fecharTudo()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handlerPrincipal.removeCallbacks(abrirConfiguracoes)
                    true
                }

                else -> false
            }
        }
    }

    private fun guardarPosicao(x: Int, y: Int) {
        app.appScope.launch {
            app.container.settingsRepository.setBubblePosition(x, y)
        }
    }

    /** Traz o app para a frente já na tela de opções da bolha. */
    private fun abrirConfiguracoesDaBolha() {
        QuickNav.pedirConfiguracoesBolha()
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(QuickNav.EXTRA_ABRIR_BOLHA, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
    }

    /**
     * Recoloca a notificação assim que o usuário a arrasta para o lado —
     * mas só se a preferência continuar ligada. Sem essa checagem, desligar
     * a notificação fixa bem no instante em que ela foi arrastada a traria
     * de volta por engano.
     */
    private fun reapresentarSeAindaAtiva() {
        if (prefsAtuais.persistentNotification) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICACAO_ID, montarNotificacao())
        }
    }

    private fun dpParaPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    private fun removerBolha() {
        val view = bolha ?: return
        handlerPrincipal.removeCallbacksAndMessages(null)
        runCatching { windowManager?.removeView(view) }
        bolha = null
        params = null
    }

    /** Mesma regra do botão da tela: protegidos, tela inicial e o próprio app ficam de fora. */
    private fun fecharTudo() {
        app.appScope.launch {
            val monitor = app.container.appMonitor
            val protegidos = app.container.repository.restrictedPackages()
            val launcher = PackageUtils.getDefaultLauncher(applicationContext)

            val alvos = monitor.snapshot().apps
                .map { it.packageName }
                .filter { it !in protegidos && it != launcher && it != packageName }

            if (alvos.isEmpty()) return@launch
            val encerrados = monitor.stopApps(alvos)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    resources.getQuantityString(R.plurals.apps_closed, encerrados, encerrados),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                getString(R.string.notifications_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun montarNotificacao(): Notification {
        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val fechar = PendingIntent.getService(
            this,
            1,
            Intent(this, QuickAccessService::class.java).setAction(ACTION_FECHAR_TUDO),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Dispara apenas quando o usuário arrasta a notificação para o lado —
        // nunca quando o próprio app a cancela (por exemplo, ao desligar a
        // opção). É o único jeito de saber que foi um descarte manual.
        val descartada = PendingIntent.getService(
            this,
            2,
            Intent(this, QuickAccessService::class.java).setAction(ACTION_NOTIFICACAO_DESCARTADA),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.quick_actions_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // setOngoing ainda ajuda em versões mais antigas do Android, mas a
            // partir do Android 14 o próprio sistema permite arrastar para o
            // lado notificações de serviço em primeiro plano mesmo assim —
            // não tem como bloquear esse gesto. O setDeleteIntent abaixo é o
            // que garante que ela reapareça na hora.
            .setOngoing(true)
            .setDeleteIntent(descartada)
            // Sem isto o Android pode segurar a notificação por até dez
            // segundos, e o usuário conclui que ela não apareceu.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(abrir)
            .addAction(R.drawable.ic_tile_close_all, getString(R.string.close_all), fechar)
            .build()
    }

    override fun onDestroy() {
        removerBolha()
        observadorPrefs?.cancel()
        vigiaPrimeiroPlano?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CANAL_ID = "quick_access"
        private const val NOTIFICACAO_ID = 2001
        private const val ACTION_FECHAR_TUDO = "com.bgcontrol.plus.QUICK_CLOSE_ALL"
        private const val ACTION_RESET_POSICAO = "com.bgcontrol.plus.RESET_BUBBLE_POSITION"
        private const val ACTION_NOTIFICACAO_DESCARTADA = "com.bgcontrol.plus.NOTIFICATION_DISMISSED"

        /** Pede ao serviço (se estiver rodando) para recentralizar a bolha agora. */
        fun resetarPosicao(context: Context) {
            val intent = Intent(context, QuickAccessService::class.java)
                .setAction(ACTION_RESET_POSICAO)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** Ritmo da checagem do app em primeiro plano, para esconder a bolha. */
        private const val INTERVALO_MS = 1_500L
        private const val INTERVALO_PARADO_MS = 10_000L

        /** Movimento acima disto é arrasto, não toque. */
        private const val TOLERANCIA_TOQUE = 12f

        /** Tempo de dedo parado que abre as configurações da bolha. */
        private const val TOQUE_LONGO_MS = 2_000L

        fun sincronizar(context: Context, ativo: Boolean) {
            val intent = Intent(context, QuickAccessService::class.java)
            if (ativo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
