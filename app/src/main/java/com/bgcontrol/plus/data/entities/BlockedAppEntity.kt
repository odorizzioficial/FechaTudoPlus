package com.bgcontrol.plus.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aplicativo bloqueado em segundo plano.
 *
 * [lastActionAt] guarda o instante da última tentativa real de encerramento.
 * [lastReclaimedBytes] e [totalReclaimedBytes] guardam a memória medida no processo
 * imediatamente antes do encerramento — só é preenchida quando a medição foi possível
 * (Shizuku disponível). Nunca recebe valores estimados.
 */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val lastActionAt: Long? = null,
    val lastActionSucceeded: Boolean? = null,
    val lastReclaimedBytes: Long? = null,
    val totalReclaimedBytes: Long = 0L
)
