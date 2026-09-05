package com.bgcontrol.plus.model

/** Aplicativo instalável nas listas, seja do usuário ou do sistema. */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isLauncher: Boolean = false,
    val isSystem: Boolean = false
)
