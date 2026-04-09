package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason

class CompositeAutoCloudBackupScheduler(
    private val delegates: List<AutoCloudBackupScheduler>,
) : AutoCloudBackupScheduler {
    constructor(vararg delegates: AutoCloudBackupScheduler) : this(delegates.toList())

    override fun schedule(reason: CloudBackupAutoBackupReason) {
        delegates.forEach { delegate ->
            delegate.schedule(reason)
        }
    }

    override suspend fun cancelPendingUploadsForManualRestore() {
        delegates.forEach { delegate ->
            delegate.cancelPendingUploadsForManualRestore()
        }
    }
}
