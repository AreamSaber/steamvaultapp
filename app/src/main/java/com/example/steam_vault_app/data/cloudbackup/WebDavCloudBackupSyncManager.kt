package com.example.steam_vault_app.data.cloudbackup

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.backup.LocalBackupPayloadCodec
import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WebDavCloudBackupSyncManager(
    private val cloudBackupRepository: CloudBackupRepository,
    private val vaultRepository: VaultRepository,
    private val webDavClient: WebDavClient = OkHttpWebDavClient(),
    private val nowIsoUtc: () -> String = ::defaultNowIsoUtc,
    context: Context? = null,
) : CloudBackupSyncManager {
    private val messages = Messages.fromContext(context)

    override suspend fun uploadCurrentBackup() = withContext(Dispatchers.IO) {
        val configuration = cloudBackupRepository.getConfiguration()
            ?: throw IllegalStateException(messages.configurationRequired)
        uploadCurrentBackup(configuration)
    }

    override suspend fun uploadCurrentBackup(configuration: WebDavBackupConfiguration) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            uploadCurrentBackupInternal(configuration.normalized())
        }
    }

    private suspend fun uploadCurrentBackupInternal(configuration: WebDavBackupConfiguration) {
        val currentStatus = cloudBackupRepository.getStatus()
        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                syncState = CloudBackupSyncState.UPLOADING,
                accountLabel = configuration.username,
                remotePath = configuration.remotePath,
                lastErrorMessage = null,
            ),
        )

        try {
            val backupPackage = vaultRepository.exportLocalBackup()
            val payload = LocalBackupPayloadCodec.encode(backupPackage)
            val uploadedAt = nowIsoUtc()
            val versionedRemotePath = CloudBackupVersioning.buildVersionedRemotePath(
                baseRemotePath = configuration.remotePath,
                uploadedAtIsoUtc = uploadedAt,
            )

            webDavClient.uploadText(configuration, payload, configuration.remotePath)
            webDavClient.uploadText(configuration, payload, versionedRemotePath)
            cleanupOldVersions(configuration)

            cloudBackupRepository.saveStatus(
                cloudBackupRepository.getStatus().copy(
                    syncState = CloudBackupSyncState.SUCCESS,
                    accountLabel = configuration.username,
                    remotePath = configuration.remotePath,
                    lastUploadAt = uploadedAt,
                    lastErrorMessage = null,
                ),
            )
        } catch (error: Exception) {
            recordFailure(configuration.username, configuration.remotePath, error)
            throw error
        }
    }

    override suspend fun restoreLatestBackup() = withContext(Dispatchers.IO) {
        val configuration = cloudBackupRepository.getConfiguration()
            ?: throw IllegalStateException(messages.configurationRequired)
        operationMutex.withLock {
            restoreBackupPayload(
                configuration = configuration,
                remotePath = configuration.remotePath,
            )
        }
    }

    override suspend fun listAvailableBackups(): List<CloudBackupRemoteVersion> = withContext(Dispatchers.IO) {
        val configuration = cloudBackupRepository.getConfiguration()
            ?: throw IllegalStateException(messages.configurationRequired)

        listManagedVersions(configuration)
    }

    override suspend fun restoreBackup(version: CloudBackupRemoteVersion) = withContext(Dispatchers.IO) {
        val configuration = cloudBackupRepository.getConfiguration()
            ?: throw IllegalStateException(messages.configurationRequired)
        operationMutex.withLock {
            restoreBackupPayload(
                configuration = configuration,
                remotePath = version.remotePath,
            )
        }
    }

    private suspend fun restoreBackupPayload(
        configuration: WebDavBackupConfiguration,
        remotePath: String,
    ) {
        val currentStatus = cloudBackupRepository.getStatus()
        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                syncState = CloudBackupSyncState.DOWNLOADING,
                accountLabel = configuration.username,
                remotePath = remotePath,
                lastErrorMessage = null,
            ),
        )

        val rawPayload = try {
            webDavClient.downloadText(configuration, remotePath)
        } catch (error: Exception) {
            recordFailure(configuration.username, remotePath, error)
            throw error
        }

        val backupPackage = try {
            LocalBackupPayloadCodec.decode(rawPayload)
        } catch (error: Exception) {
            recordFailure(configuration.username, remotePath, error)
            throw error
        }

        try {
            vaultRepository.restoreLocalBackup(backupPackage)
            cloudBackupRepository.saveStatus(
                cloudBackupRepository.getStatus().copy(
                    syncState = CloudBackupSyncState.SUCCESS,
                    accountLabel = configuration.username,
                    remotePath = remotePath,
                    lastDownloadAt = nowIsoUtc(),
                    lastErrorMessage = null,
                ),
            )
        } catch (error: Exception) {
            recordFailure(configuration.username, remotePath, error)
            throw error
        }
    }

    private suspend fun cleanupOldVersions(
        configuration: WebDavBackupConfiguration,
    ) {
        listManagedVersions(configuration)
            .drop(MAX_RETAINED_REMOTE_VERSIONS)
            .forEach { version ->
                webDavClient.delete(configuration, version.remotePath)
            }
    }

    private suspend fun listManagedVersions(
        configuration: WebDavBackupConfiguration,
    ): List<CloudBackupRemoteVersion> {
        val historyDirectoryPath = CloudBackupVersioning.buildHistoryDirectoryPath(configuration.remotePath)
        val remoteEntries = webDavClient.listFiles(configuration, historyDirectoryPath)
        return CloudBackupVersioning.sortNewestFirst(
            remoteEntries.mapNotNull { entry ->
                CloudBackupVersioning.toRemoteVersion(configuration.remotePath, entry)
            },
        )
    }

    private suspend fun recordFailure(
        accountLabel: String,
        remotePath: String,
        error: Exception,
    ) {
        val currentStatus = cloudBackupRepository.getStatus()
        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                syncState = CloudBackupSyncState.ERROR,
                accountLabel = accountLabel,
                remotePath = remotePath,
                lastErrorMessage = error.message ?: messages.genericFailure,
            ),
        )
    }

    private data class Messages(
        val configurationRequired: String,
        val genericFailure: String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        configurationRequired = "Please finish WebDAV configuration first.",
                        genericFailure = "Cloud backup failed.",
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    configurationRequired = appContext.getString(R.string.cloud_backup_sync_configuration_required),
                    genericFailure = appContext.getString(R.string.cloud_backup_sync_failed_generic),
                )
            }
        }
    }

    private companion object {
        private const val MAX_RETAINED_REMOTE_VERSIONS = 5
        private val operationMutex = Mutex()

        private fun defaultNowIsoUtc(): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            return formatter.format(Date())
        }
    }
}
