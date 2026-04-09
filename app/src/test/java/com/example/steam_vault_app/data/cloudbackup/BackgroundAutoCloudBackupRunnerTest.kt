package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundAutoCloudBackupRunnerTest {
    @Test
    fun run_uploadsWithBackgroundConfigurationAndMarksSuccess() = runBlocking {
        val repository = FakeBackgroundSyncCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(
                syncState = CloudBackupSyncState.IDLE,
            ),
        )
        val syncManager = FakeCloudBackupSyncManager(repository)
        val runner = BackgroundAutoCloudBackupRunner(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            dispatcher = FakeBackgroundCloudBackupWorkDispatcher(),
            nowMillis = { FIXED_NOW_MILLIS },
        )

        val result = runner.run(
            reason = CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED,
            requestedAtMillis = FIXED_NOW_MILLIS - 1_000L,
        )

        assertEquals(BackgroundAutoCloudBackupResult.SUCCESS, result)
        assertEquals(1, syncManager.uploadCalls.size)
        assertEquals(demoConfiguration(), syncManager.uploadCalls.single())
        assertEquals(CloudBackupAutoBackupState.SUCCESS, repository.status.autoBackupState)
        assertEquals(0, repository.status.autoBackupFailureCount)
        assertNull(repository.status.autoBackupNextRunAt)
    }

    @Test
    fun run_skipsWhenForegroundUploadAlreadyCoveredRequest() = runBlocking {
        val repository = FakeBackgroundSyncCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(
                syncState = CloudBackupSyncState.SUCCESS,
                lastUploadAt = CloudBackupAutoBackupTiming.formatIsoUtc(FIXED_NOW_MILLIS),
            ),
        )
        val syncManager = FakeCloudBackupSyncManager(repository)
        val runner = BackgroundAutoCloudBackupRunner(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            dispatcher = FakeBackgroundCloudBackupWorkDispatcher(),
            nowMillis = { FIXED_NOW_MILLIS + 5_000L },
        )

        val result = runner.run(
            reason = CloudBackupAutoBackupReason.SECURITY_SETTINGS_CHANGED,
            requestedAtMillis = FIXED_NOW_MILLIS - 1_000L,
        )

        assertEquals(BackgroundAutoCloudBackupResult.SKIPPED, result)
        assertEquals(emptyList<WebDavBackupConfiguration>(), syncManager.uploadCalls)
    }

    @Test
    fun run_schedulesRetryAfterFailure() = runBlocking {
        val repository = FakeBackgroundSyncCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(
                syncState = CloudBackupSyncState.ERROR,
            ),
        )
        val dispatcher = FakeBackgroundCloudBackupWorkDispatcher()
        val syncManager = FakeCloudBackupSyncManager(
            repository = repository,
            uploadError = IllegalStateException("boom"),
        )
        val runner = BackgroundAutoCloudBackupRunner(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            dispatcher = dispatcher,
            nowMillis = { FIXED_NOW_MILLIS },
        )

        val result = runner.run(
            reason = CloudBackupAutoBackupReason.CONFIGURATION_CHANGED,
            requestedAtMillis = FIXED_NOW_MILLIS - 5_000L,
        )

        assertEquals(BackgroundAutoCloudBackupResult.RETRY_SCHEDULED, result)
        assertEquals(CloudBackupAutoBackupState.RETRY_SCHEDULED, repository.status.autoBackupState)
        assertEquals(1, repository.status.autoBackupFailureCount)
        assertEquals(1, dispatcher.retryRequests.size)
        assertEquals(30_000L, dispatcher.retryRequests.single().delayMillis)
    }

    private class FakeBackgroundSyncCloudBackupRepository(
        private var configuration: WebDavBackupConfiguration?,
        var status: CloudBackupStatus,
    ) : BackgroundSyncCloudBackupRepository {
        var refreshSnapshotCalls = 0

        override suspend fun getConfiguration(): WebDavBackupConfiguration? = configuration

        override suspend fun saveConfiguration(configuration: WebDavBackupConfiguration) {
            this.configuration = configuration
        }

        override suspend fun clearConfiguration() {
            configuration = null
            status = CloudBackupStatus()
        }

        override suspend fun getStatus(): CloudBackupStatus = status

        override suspend fun saveStatus(status: CloudBackupStatus) {
            this.status = status
        }

        override suspend fun getBackgroundSyncConfiguration(): WebDavBackupConfiguration? = configuration

        override suspend fun refreshBackgroundSyncConfigurationSnapshot(): Boolean {
            refreshSnapshotCalls += 1
            return configuration != null
        }

        override suspend fun clearBackgroundSyncConfigurationSnapshot() = Unit
    }

    private class FakeCloudBackupSyncManager(
        private val repository: FakeBackgroundSyncCloudBackupRepository,
        private val uploadError: Exception? = null,
    ) : CloudBackupSyncManager {
        val uploadCalls = mutableListOf<WebDavBackupConfiguration>()

        override suspend fun uploadCurrentBackup() {
            error("not used in background runner test")
        }

        override suspend fun uploadCurrentBackup(configuration: WebDavBackupConfiguration) {
            uploadCalls += configuration
            if (uploadError != null) {
                repository.saveStatus(
                    repository.getStatus().copy(
                        syncState = CloudBackupSyncState.ERROR,
                        lastErrorMessage = uploadError.message,
                    ),
                )
                throw uploadError
            }
            repository.saveStatus(
                repository.getStatus().copy(
                    syncState = CloudBackupSyncState.SUCCESS,
                    accountLabel = configuration.username,
                    remotePath = configuration.remotePath,
                    lastUploadAt = CloudBackupAutoBackupTiming.formatIsoUtc(FIXED_NOW_MILLIS),
                    lastErrorMessage = null,
                ),
            )
        }

        override suspend fun restoreLatestBackup() = Unit

        override suspend fun listAvailableBackups(): List<CloudBackupRemoteVersion> = emptyList()

        override suspend fun restoreBackup(version: CloudBackupRemoteVersion) = Unit
    }

    private class FakeBackgroundCloudBackupWorkDispatcher : BackgroundCloudBackupWorkDispatcher {
        val scheduleRequests = mutableListOf<Request>()
        val retryRequests = mutableListOf<Request>()
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
            retryRequests += Request(reason, requestedAtMillis, delayMillis)
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
