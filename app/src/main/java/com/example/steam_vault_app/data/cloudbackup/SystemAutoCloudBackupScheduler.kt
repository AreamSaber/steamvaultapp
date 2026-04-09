package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SystemAutoCloudBackupScheduler(
    private val cloudBackupRepository: BackgroundSyncCloudBackupRepository,
    private val dispatcher: BackgroundCloudBackupWorkDispatcher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val debounceMillis: Long = 10_000L,
    private val minUploadIntervalMillis: Long = 60_000L,
    private val fallbackGraceMillis: Long = 20_000L,
) : AutoCloudBackupScheduler {
    override fun schedule(reason: CloudBackupAutoBackupReason) {
        scope.launch {
            runCatching {
                cloudBackupRepository.refreshBackgroundSyncConfigurationSnapshot()
            }
            val backgroundConfiguration = runCatching {
                cloudBackupRepository.getBackgroundSyncConfiguration()
            }.getOrNull() ?: return@launch
            val currentStatus = runCatching {
                cloudBackupRepository.getStatus()
            }.getOrNull() ?: return@launch

            val requestedAtMillis = nowMillis()
            val delayMillis = CloudBackupAutoBackupTiming.computeDelayMillis(
                lastUploadAt = currentStatus.lastUploadAt,
                nowMillis = requestedAtMillis,
                debounceMillis = debounceMillis,
                minUploadIntervalMillis = minUploadIntervalMillis,
            ) + fallbackGraceMillis

            dispatcher.schedule(
                reason = reason,
                requestedAtMillis = requestedAtMillis,
                delayMillis = delayMillis,
            )
        }
    }

    override suspend fun cancelPendingUploadsForManualRestore() {
        dispatcher.cancelPending()
    }
}
