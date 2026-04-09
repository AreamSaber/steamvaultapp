package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InProcessAutoCloudBackupSchedulerTest {
    @Test
    fun schedule_debouncesRapidChangesAndUsesLatestReason() = runBlocking {
        val repository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(syncState = CloudBackupSyncState.IDLE),
        )
        val syncManager = FakeCloudBackupSyncManager(repository = repository)
        val scheduler = InProcessAutoCloudBackupScheduler(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            scope = this,
            nowMillis = { FIXED_NOW_MILLIS },
            debounceMillis = 40L,
            minUploadIntervalMillis = 0L,
            retryDelaysMillis = emptyList(),
        )

        scheduler.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        delay(10L)
        scheduler.schedule(CloudBackupAutoBackupReason.CONFIGURATION_CHANGED)
        delay(80L)

        assertEquals(1, syncManager.uploadAttempts)
        assertEquals(CloudBackupAutoBackupState.SUCCESS, repository.status.autoBackupState)
        assertEquals(CloudBackupAutoBackupReason.CONFIGURATION_CHANGED, repository.status.autoBackupReason)
    }

    @Test
    fun schedule_retriesFailedAutomaticUpload() = runBlocking {
        val repository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(syncState = CloudBackupSyncState.IDLE),
        )
        val syncManager = FakeCloudBackupSyncManager(
            repository = repository,
            failingUploads = 1,
        )
        val scheduler = InProcessAutoCloudBackupScheduler(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            scope = this,
            nowMillis = { FIXED_NOW_MILLIS },
            debounceMillis = 0L,
            minUploadIntervalMillis = 0L,
            retryDelaysMillis = listOf(0L),
        )

        scheduler.schedule(CloudBackupAutoBackupReason.SECURITY_SETTINGS_CHANGED)
        delay(80L)

        assertEquals(2, syncManager.uploadAttempts)
        assertEquals(CloudBackupAutoBackupState.SUCCESS, repository.status.autoBackupState)
        assertEquals(0, repository.status.autoBackupFailureCount)
        assertEquals(CloudBackupAutoBackupReason.SECURITY_SETTINGS_CHANGED, repository.status.autoBackupReason)
    }

    @Test
    fun schedule_respectsRecentUploadCooldown() = runBlocking {
        val repository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(
                syncState = CloudBackupSyncState.SUCCESS,
                lastUploadAt = formatIsoUtc(FIXED_NOW_MILLIS),
            ),
        )
        val syncManager = FakeCloudBackupSyncManager(repository = repository)
        val scheduler = InProcessAutoCloudBackupScheduler(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            scope = this,
            nowMillis = { FIXED_NOW_MILLIS },
            debounceMillis = 0L,
            minUploadIntervalMillis = 1_000L,
            retryDelaysMillis = emptyList(),
        )

        scheduler.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        delay(120L)

        assertEquals(0, syncManager.uploadAttempts)
        assertEquals(CloudBackupAutoBackupState.SCHEDULED, repository.status.autoBackupState)
        assertNotNull(repository.status.autoBackupNextRunAt)
    }

    @Test
    fun cancelPendingUploadsForManualRestore_clearsQueuedUploadAndMarksPausedState() = runBlocking {
        val repository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(syncState = CloudBackupSyncState.IDLE),
        )
        val syncManager = FakeCloudBackupSyncManager(repository = repository)
        val scheduler = InProcessAutoCloudBackupScheduler(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            scope = this,
            nowMillis = { FIXED_NOW_MILLIS },
            debounceMillis = 50L,
            minUploadIntervalMillis = 0L,
            retryDelaysMillis = emptyList(),
        )

        scheduler.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        scheduler.cancelPendingUploadsForManualRestore()
        delay(80L)

        assertEquals(0, syncManager.uploadAttempts)
        assertEquals(CloudBackupAutoBackupState.PAUSED_AFTER_RESTORE, repository.status.autoBackupState)
        assertEquals(CloudBackupAutoBackupReason.MANUAL_RESTORE, repository.status.autoBackupReason)
        assertEquals(null, repository.status.autoBackupNextRunAt)
    }

    @Test
    fun cancelPendingUploadsForManualRestore_ignoresInFlightUploadCompletion() = runBlocking {
        val repository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(syncState = CloudBackupSyncState.IDLE),
        )
        val uploadStarted = CompletableDeferred<Unit>()
        val releaseUpload = CompletableDeferred<Unit>()
        val syncManager = FakeCloudBackupSyncManager(
            repository = repository,
            beforeSuccessfulUploadCompletes = {
                uploadStarted.complete(Unit)
                releaseUpload.await()
            },
        )
        val scheduler = InProcessAutoCloudBackupScheduler(
            cloudBackupRepository = repository,
            cloudBackupSyncManager = syncManager,
            scope = this,
            nowMillis = { FIXED_NOW_MILLIS },
            debounceMillis = 0L,
            minUploadIntervalMillis = 0L,
            retryDelaysMillis = emptyList(),
        )

        scheduler.schedule(CloudBackupAutoBackupReason.CONFIGURATION_CHANGED)
        uploadStarted.await()
        val cancelRestore = async {
            scheduler.cancelPendingUploadsForManualRestore()
        }
        delay(10L)
        releaseUpload.complete(Unit)
        cancelRestore.await()
        delay(40L)

        assertEquals(1, syncManager.uploadAttempts)
        assertEquals(CloudBackupAutoBackupState.PAUSED_AFTER_RESTORE, repository.status.autoBackupState)
        assertEquals(CloudBackupAutoBackupReason.MANUAL_RESTORE, repository.status.autoBackupReason)
    }

    private class FakeCloudBackupRepository(
        private var configuration: WebDavBackupConfiguration?,
        var status: CloudBackupStatus,
    ) : CloudBackupRepository {
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
    }

    private class FakeCloudBackupSyncManager(
        private val repository: FakeCloudBackupRepository,
        private val failingUploads: Int = 0,
        private val beforeSuccessfulUploadCompletes: (suspend () -> Unit)? = null,
    ) : CloudBackupSyncManager {
        var uploadAttempts: Int = 0

        override suspend fun uploadCurrentBackup() {
            uploadAttempts += 1
            if (uploadAttempts <= failingUploads) {
                repository.saveStatus(
                    repository.getStatus().copy(
                        syncState = CloudBackupSyncState.ERROR,
                        lastErrorMessage = "boom",
                    ),
                )
                throw IllegalStateException("boom")
            }

            beforeSuccessfulUploadCompletes?.invoke()
            repository.saveStatus(
                repository.getStatus().copy(
                    syncState = CloudBackupSyncState.SUCCESS,
                    lastUploadAt = formatIsoUtc(FIXED_NOW_MILLIS),
                    lastErrorMessage = null,
                ),
            )
        }

        override suspend fun restoreLatestBackup() = Unit

        override suspend fun listAvailableBackups(): List<CloudBackupRemoteVersion> = emptyList()

        override suspend fun restoreBackup(version: CloudBackupRemoteVersion) = Unit
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

        private fun formatIsoUtc(timestampMillis: Long): String {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(timestampMillis))
        }
    }
}
