package com.example.steam_vault_app.data.cloudbackup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import java.util.concurrent.TimeUnit

interface BackgroundCloudBackupWorkDispatcher {
    fun schedule(
        reason: CloudBackupAutoBackupReason,
        requestedAtMillis: Long,
        delayMillis: Long,
    )

    fun scheduleRetry(
        reason: CloudBackupAutoBackupReason,
        requestedAtMillis: Long,
        delayMillis: Long,
    )

    fun cancelPending()
}

class WorkManagerBackgroundCloudBackupWorkDispatcher(
    context: Context,
) : BackgroundCloudBackupWorkDispatcher {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(
        reason: CloudBackupAutoBackupReason,
        requestedAtMillis: Long,
        delayMillis: Long,
    ) {
        workManager.enqueueUniqueWork(
            AutoCloudBackupWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            buildRequest(
                reason = reason,
                requestedAtMillis = requestedAtMillis,
                delayMillis = delayMillis,
            ),
        )
    }

    override fun scheduleRetry(
        reason: CloudBackupAutoBackupReason,
        requestedAtMillis: Long,
        delayMillis: Long,
    ) {
        workManager.enqueueUniqueWork(
            AutoCloudBackupWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            buildRequest(
                reason = reason,
                requestedAtMillis = requestedAtMillis,
                delayMillis = delayMillis,
            ),
        )
    }

    override fun cancelPending() {
        workManager.cancelUniqueWork(AutoCloudBackupWorker.UNIQUE_WORK_NAME)
    }

    private fun buildRequest(
        reason: CloudBackupAutoBackupReason,
        requestedAtMillis: Long,
        delayMillis: Long,
    ) = OneTimeWorkRequestBuilder<AutoCloudBackupWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
        .setInputData(
            workDataOf(
                AutoCloudBackupWorker.KEY_REASON to reason.name,
                AutoCloudBackupWorker.KEY_REQUESTED_AT_MILLIS to requestedAtMillis,
            ),
        )
        .addTag(AutoCloudBackupWorker.WORK_TAG)
        .build()
}
