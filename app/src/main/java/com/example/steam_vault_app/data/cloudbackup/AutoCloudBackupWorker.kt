package com.example.steam_vault_app.data.cloudbackup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.data.repository.LocalCloudBackupRepository
import com.example.steam_vault_app.data.repository.LocalVaultRepository
import com.example.steam_vault_app.data.security.LocalMasterPasswordManager
import com.example.steam_vault_app.data.security.LocalVaultCryptography
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason

class AutoCloudBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON)
            ?.let { rawValue -> runCatching { CloudBackupAutoBackupReason.valueOf(rawValue) }.getOrNull() }
            ?: return Result.success()
        val requestedAtMillis = inputData.getLong(KEY_REQUESTED_AT_MILLIS, -1L)
            .takeIf { it > 0L }
            ?: return Result.success()

        DataMessageCatalog.initialize(applicationContext)
        val masterPasswordManager = LocalMasterPasswordManager(applicationContext)
        val vaultCryptography = LocalVaultCryptography(applicationContext)
        val cloudBackupRepository = LocalCloudBackupRepository(
            context = applicationContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = vaultCryptography,
        )
        val vaultRepository = LocalVaultRepository(
            context = applicationContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = vaultCryptography,
        )
        val runner = BackgroundAutoCloudBackupRunner(
            cloudBackupRepository = cloudBackupRepository,
            cloudBackupSyncManager = WebDavCloudBackupSyncManager(
                cloudBackupRepository = cloudBackupRepository,
                vaultRepository = vaultRepository,
                webDavClient = OkHttpWebDavClient(context = applicationContext),
                context = applicationContext,
            ),
            dispatcher = WorkManagerBackgroundCloudBackupWorkDispatcher(applicationContext),
        )

        runner.run(
            reason = reason,
            requestedAtMillis = requestedAtMillis,
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "steam_vault_auto_cloud_backup"
        const val WORK_TAG = "steam_vault_auto_cloud_backup"
        const val KEY_REASON = "reason"
        const val KEY_REQUESTED_AT_MILLIS = "requested_at_millis"
    }
}
