package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemAutoCloudBackupSchedulerTest {
    @Test
    fun schedule_enqueuesBackgroundWorkWithGraceDelay() {
        val dispatcher = FakeBackgroundCloudBackupWorkDispatcher()
        val repository = FakeBackgroundSyncCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scheduler = SystemAutoCloudBackupScheduler(
            cloudBackupRepository = repository,
            dispatcher = dispatcher,
            scope = scope,
            nowMillis = { FIXED_NOW_MILLIS },
            debounceMillis = 10_000L,
            minUploadIntervalMillis = 60_000L,
            fallbackGraceMillis = 20_000L,
        )

        scheduler.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)

        assertEquals(1, repository.refreshSnapshotCalls)
        assertEquals(1, dispatcher.scheduleRequests.size)
        assertEquals(30_000L, dispatcher.scheduleRequests.single().delayMillis)
        assertEquals(
            CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED,
            dispatcher.scheduleRequests.single().reason,
        )
    }

    @Test
    fun cancelPendingUploadsForManualRestore_cancelsPendingWork() {
        val dispatcher = FakeBackgroundCloudBackupWorkDispatcher()
        val scheduler = SystemAutoCloudBackupScheduler(
            cloudBackupRepository = FakeBackgroundSyncCloudBackupRepository(
                configuration = demoConfiguration(),
                status = CloudBackupStatus(),
            ),
            dispatcher = dispatcher,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowMillis = { FIXED_NOW_MILLIS },
        )

        kotlinx.coroutines.runBlocking {
            scheduler.cancelPendingUploadsForManualRestore()
        }

        assertEquals(1, dispatcher.cancelCalls)
    }

    private class FakeBackgroundSyncCloudBackupRepository(
        private var configuration: WebDavBackupConfiguration?,
        private val status: CloudBackupStatus,
    ) : BackgroundSyncCloudBackupRepository {
        var refreshSnapshotCalls = 0

        override suspend fun getConfiguration(): WebDavBackupConfiguration? = configuration

        override suspend fun saveConfiguration(configuration: WebDavBackupConfiguration) {
            this.configuration = configuration
        }

        override suspend fun clearConfiguration() {
            configuration = null
        }

        override suspend fun getStatus(): CloudBackupStatus = status

        override suspend fun saveStatus(status: CloudBackupStatus) = Unit

        override suspend fun getBackgroundSyncConfiguration(): WebDavBackupConfiguration? = configuration

        override suspend fun refreshBackgroundSyncConfigurationSnapshot(): Boolean {
            refreshSnapshotCalls += 1
            return configuration != null
        }

        override suspend fun clearBackgroundSyncConfigurationSnapshot() = Unit
    }

    private class FakeBackgroundCloudBackupWorkDispatcher : BackgroundCloudBackupWorkDispatcher {
        val scheduleRequests = mutableListOf<Request>()
        var cancelCalls = 0

        override fun schedule(
            reason: CloudBackupAutoBackupReason,
            requestedAtMillis: Long,
            delayMillis: Long,
        ) {
            scheduleRequests += Request(reason, requestedAtMillis, delayMillis)
        }

        override fun scheduleRetry(
            reason: CloudBackupAutoBackupReason,
            requestedAtMillis: Long,
            delayMillis: Long,
        ) {
            error("retry should not be used by the system scheduler")
        }

        override fun cancelPending() {
            cancelCalls += 1
        }

        data class Request(
            val reason: CloudBackupAutoBackupReason,
            val requestedAtMillis: Long,
            val delayMillis: Long,
        )
    }

    companion object {
        private const val FIXED_NOW_MILLIS = 1_775_520_000_000L

        private fun demoConfiguration(): WebDavBackupConfiguration {
            return WebDavBackupConfiguration(
                serverUrl = "https://dav.jianguoyun.com/dav",
                username = "demo@example.com",
                appPassword = "token-123",
                remotePath = "/SteamVault/backup.json",
            )
        }
    }
}
