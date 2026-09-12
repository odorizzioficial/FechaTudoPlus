package com.bgcontrol.plus.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aplicativo congelado pelo usuário.
 *
 * Congelar = `pm disable-user --user 0 <pkg>` via Shizuku.
 * Descongelar = `pm enable <pkg>` via Shizuku.
 * O app some completamente da gaveta e de todos os processos enquanto
 * estiver congelado — mais agressivo que o bloqueio, que apenas suspende.
 */
@Entity(tableName = "frozen_apps")
data class FrozenAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val frozenAt: Long = System.currentTimeMillis(),
    /** True = desabilitado no sistema. False = temporariamente descongelado. */
    val isEnabled: Boolean = true
)
