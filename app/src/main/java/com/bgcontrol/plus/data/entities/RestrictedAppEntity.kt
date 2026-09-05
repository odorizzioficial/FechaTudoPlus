package com.bgcontrol.plus.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Aplicativo protegido: nunca é encerrado por "Fechar tudo". */
@Entity(tableName = "restricted_apps")
data class RestrictedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
)
