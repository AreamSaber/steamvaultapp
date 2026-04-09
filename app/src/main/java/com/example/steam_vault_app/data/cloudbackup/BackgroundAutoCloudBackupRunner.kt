package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager

enum class BackgroundAutoCloudBackupResult {
    SKIPPED,
    SUCCESS,
    RETRY_SCHEDULED,
    FAILED,
}

class BackgroundAutoCloudBackupRunner(
    private val cloudBackupRepository: BackgroundSyncCloudBackupRepository,
    private val cloudBackupSyncManager: CloudBackupSyncManager,
    private val dispatcher: BackgroundCloudBackupWorkDispatcher? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val retryDelaysMillis: List<Long> = listOf(30_000L, 120_000L, 300_000L),
) {
    suspend fun run(
        reason: CloudBackupAutoBackupReason,
        requestedAtMillis: Long,
    ): BackgroundAutoCloudBackupResult {
        runCatching { cloudBackupRepository.refreshBackgroundSyncConfigurationSnapshot() }
        val configuration = cloudBackupRepository.getBackgroundSyncConfiguration()
            ?: return BackgroundAutoCloudBackupResult.SKIPPED

        val lastUploadMillis = CloudBackupAutoBackupTiming.parseIsoUtc(
            cloudBackupRepository.getStatus().lastUploadAt,
        )
        if (lastUploadMillis != null && lastUploadMillis >= requestedAtMillis) {
            return BackgroundAutoCloudBackupResult.SKIPPED
        }

        return try {
            cloudBackupSyncManager.uploadCurrentBackup(configuration)
            val currentStatus = cloudBackupRepository.getStatus()
            cloudBackupRepository.saveStatus(
                currentStatus.copy(
                    accountLabel = configuration.username,
                    remotePath = configuration.remotePath,
                    autoBackupState = CloudBackupAutoBackupState.SUCCESS,
                    autoBackupReason = reason,
                    autoBackupUpdatedAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis()),
                    autoBackupNextRunAt = null,
                    autoBackupFailureCount = 0,
                ),
            )
            BackgroundAutoCloudBackupResult.SUCCESS
        } catch (_: Exception) {
            val currentStatus = cloudBackupRepository.getStatus()
            val nextFailureCount = currentStatus.autoBackupFailureCount + 1
            val retryDelayMillis = retryDelaysMillis.getOrNull(nextFailureCount - 1)
            cloudBackupRepository.saveStatus(
                currentStatus.copy(
                    accountLabel = configuration.username,
                    remotePath = configuration.remotePath,
                    autoBackupState = if (retryDelayMillis == null) {
                        CloudBackupAutoBackupState.ERROR
                    } else {
                        CloudBackupAutoBackupState.RETRY_SCHEDULED
                    },
                    autoBackupReason = reason,
                    autoBackupUpdatedAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis()),
                    autoBackupNextRunAt = retryDelayMillis?.let {
                        CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis() + it)
                    },
                    autoBackupFailureCount = nextFailureCount,
                ),
            )
            if (retryDelayMillis == null) {
                BackgroundAutoCloudBackupResult.FAILED
            } else {
                dispatcher?.scheduleRetry(
                    reason = reason,
                    requestedAtMillis = requestedAtMillis,
                    delayMillis = retryDelayMillis,
                )
                BackgroundAutoCloudBackupResult.RETRY_SCHEDULED
            }
        }
    }
}
