package com.bgcontrol.plus.qs

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.bgcontrol.plus.BgControlApp
import com.bgcontrol.plus.R
import com.bgcontrol.plus.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bloco das Configurações Rápidas: fecha os aplicativos em execução direto da
 * barra de notificações, sem abrir o app.
 *
 * As mesmas garantias da aba "Em execução" valem aqui: os aplicativos da lista
 * Restritos, a tela inicial e o próprio BG Control Plus nunca são encerrados.
 */
@RequiresApi(Build.VERSION_CODES.N)
class CloseAllTileService : TileService() {

    private val app: BgControlApp get() = application as BgControlApp

    /** O sistema avisa quando o bloco entra ou sai do painel — é a fonte da verdade. */
    override fun onTileAdded() {
        super.onTileAdded()
        registrarEstado(true)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        registrarEstado(false)
    }

    private fun registrarEstado(adicionado: Boolean) {
        app.appScope.launch {
            app.container.settingsRepository.setQuickTileAdded(adicionado)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(R.string.close_all)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.quick_tile_subtitle)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            // Com a tela bloqueada, o Android exige o desbloqueio antes de agir.
            unlockAndRun { fecharTudo() }
        } else {
            fecharTudo()
        }
    }

    /**
     * Roda no escopo do Application: o serviço do bloco é desvinculado assim que
     * o painel fecha, e o encerramento precisa sobreviver a isso.
     */
    private fun fecharTudo() {
        app.appScope.launch {
            val monitor = app.container.appMonitor
            val protegidos = app.container.repository.restrictedPackages()
            val launcher = PackageUtils.getDefaultLauncher(applicationContext)

            val alvos = monitor.snapshot().apps
                .map { it.packageName }
                .filter {
                    it !in protegidos && it != launcher && it != applicationContext.packageName
                }

            if (alvos.isEmpty()) {
                aviso(getString(R.string.tile_nothing_to_close))
                return@launch
            }

            val encerrados = monitor.stopApps(alvos)
            aviso(
                resources.getQuantityString(R.plurals.apps_closed, encerrados, encerrados)
            )
        }
    }

    private suspend fun aviso(texto: String) = withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, texto, Toast.LENGTH_SHORT).show()
    }
}
