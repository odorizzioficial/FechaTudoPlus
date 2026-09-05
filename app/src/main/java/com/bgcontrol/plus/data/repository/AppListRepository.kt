package com.bgcontrol.plus.data.repository

import com.bgcontrol.plus.data.dao.BlockedAppDao
import com.bgcontrol.plus.data.dao.RestrictedAppDao
import com.bgcontrol.plus.data.entities.BlockedAppEntity
import com.bgcontrol.plus.data.entities.RestrictedAppEntity
import com.bgcontrol.plus.model.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Resultado de uma tentativa de adicionar um app a uma das listas. */
sealed interface AddResult {
    data object Success : AddResult
}

/**
 * Fonte única de verdade das duas listas.
 *
 * As listas são independentes e podem se sobrepor: um app restrito nunca é
 * encerrado pelo "Fechar Tudo", e se também estiver bloqueado ele é encerrado
 * ao sair do primeiro plano. Cada pacote continua único dentro de cada lista,
 * garantido pela chave primária das tabelas.
 */
class AppListRepository(
    private val restrictedDao: RestrictedAppDao,
    private val blockedDao: BlockedAppDao
) {

    val restrictedApps: Flow<List<RestrictedAppEntity>> = restrictedDao.observeAll()
    val blockedApps: Flow<List<BlockedAppEntity>> = blockedDao.observeAll()
    val totalReclaimedBytes: Flow<Long> = blockedDao.observeTotalReclaimed().map { it ?: 0L }

    suspend fun restrictedPackages(): Set<String> =
        restrictedDao.getAll().map { it.packageName }.toSet()

    suspend fun protectedPackages(): Set<String> =
        restrictedDao.getEnabledPackages().toSet()

    suspend fun blockedPackages(): Set<String> =
        blockedDao.getAll().map { it.packageName }.toSet()

    suspend fun addRestricted(app: InstalledApp): AddResult {
        restrictedDao.insert(
            RestrictedAppEntity(packageName = app.packageName, appName = app.appName)
        )
        return AddResult.Success
    }

    suspend fun addRestricted(apps: List<InstalledApp>): List<AddResult> =
        apps.map { addRestricted(it) }

    suspend fun addBlocked(app: InstalledApp): AddResult {
        blockedDao.insert(
            BlockedAppEntity(packageName = app.packageName, appName = app.appName)
        )
        return AddResult.Success
    }

    suspend fun addBlocked(apps: List<InstalledApp>): List<AddResult> =
        apps.map { addBlocked(it) }

    suspend fun setRestrictedEnabled(packageName: String, enabled: Boolean) =
        restrictedDao.setEnabled(packageName, enabled)

    suspend fun setBlockedEnabled(packageName: String, enabled: Boolean) =
        blockedDao.setEnabled(packageName, enabled)

    suspend fun removeRestricted(packageName: String) = restrictedDao.delete(packageName)

    suspend fun removeBlocked(packageName: String) = blockedDao.delete(packageName)

    suspend fun enabledBlockedApps(): List<BlockedAppEntity> = blockedDao.getEnabled()

    /** Registra uma tentativa real de encerramento de um app bloqueado. */
    suspend fun registerBlockedAction(
        packageName: String,
        succeeded: Boolean,
        reclaimedBytes: Long?
    ) = blockedDao.registerAction(
        packageName = packageName,
        timestamp = System.currentTimeMillis(),
        succeeded = succeeded,
        reclaimed = reclaimedBytes,
        reclaimedIncrement = if (succeeded) (reclaimedBytes ?: 0L) else 0L
    )
}
