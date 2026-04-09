package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.data.backup.LocalBackupPayloadCodec
import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.ImportDraft
import com.example.steam_vault_app.domain.model.LocalBackupPackage
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.model.VaultBlob
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.MasterPasswordBackupSnapshot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavCloudBackupSyncManagerTest {
    @Test
    fun uploadCurrentBackup_updatesStatusUploadsVersionAndCleansUpOldSnapshots() = runBlocking {
        val cloudRepository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(),
        )
        val vaultRepository = FakeVaultRepository()
        val client = FakeWebDavClient(
            listedFiles = listOf(
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T160000Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T16:00:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T155900Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T15:59:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T155800Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T15:58:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T155700Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T15:57:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T155600Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T15:56:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T155500Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T15:55:00Z",
                ),
            ),
        )
        val manager = WebDavCloudBackupSyncManager(
            cloudBackupRepository = cloudRepository,
            vaultRepository = vaultRepository,
            webDavClient = client,
            nowIsoUtc = { "2026-04-07T16:00:00Z" },
        )

        manager.uploadCurrentBackup()

        assertEquals(2, client.uploads.size)
        assertEquals("/SteamVault/backup.json", client.uploads[0].remotePath)
        assertEquals("/SteamVault/_history/backup--20260407T160000Z.json", client.uploads[1].remotePath)
        assertNotNull(client.uploads[0].payload)
        assertEquals(CloudBackupSyncState.SUCCESS, cloudRepository.status.syncState)
        assertEquals("2026-04-07T16:00:00Z", cloudRepository.status.lastUploadAt)
        assertEquals(demoConfiguration().remotePath, cloudRepository.status.remotePath)
        assertEquals(listOf("/SteamVault/_history/backup--20260407T155500Z.json"), client.deletedPaths)
    }

    @Test
    fun restoreLatestBackup_updatesStatusAndRestoresVault() = runBlocking {
        val backupPackage = demoBackupPackage()
        val cloudRepository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(),
        )
        val vaultRepository = FakeVaultRepository()
        val client = FakeWebDavClient(
            downloadedPayload = LocalBackupPayloadCodec.encode(backupPackage),
        )
        val manager = WebDavCloudBackupSyncManager(
            cloudBackupRepository = cloudRepository,
            vaultRepository = vaultRepository,
            webDavClient = client,
            nowIsoUtc = { "2026-04-07T16:05:00Z" },
        )

        manager.restoreLatestBackup()

        assertEquals(backupPackage, vaultRepository.restoredBackupPackage)
        assertEquals(CloudBackupSyncState.SUCCESS, cloudRepository.status.syncState)
        assertEquals("2026-04-07T16:05:00Z", cloudRepository.status.lastDownloadAt)
        assertEquals("/SteamVault/backup.json", client.downloadedPaths.single())
    }

    @Test
    fun restoreLatestBackup_updatesStatusFromMirrorAfterLocalRestore() = runBlocking {
        val backupPackage = demoBackupPackage()
        val cloudRepository = PostRestoreMirrorCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(),
        )
        val vaultRepository = FakeVaultRepository(
            onRestore = {
                cloudRepository.lock()
            },
        )
        val client = FakeWebDavClient(
            downloadedPayload = LocalBackupPayloadCodec.encode(backupPackage),
        )
        val manager = WebDavCloudBackupSyncManager(
            cloudBackupRepository = cloudRepository,
            vaultRepository = vaultRepository,
            webDavClient = client,
            nowIsoUtc = { "2026-04-07T16:05:00Z" },
        )

        manager.restoreLatestBackup()

        assertEquals(backupPackage, vaultRepository.restoredBackupPackage)
        assertEquals(CloudBackupSyncState.SUCCESS, cloudRepository.status.syncState)
        assertEquals("2026-04-07T16:05:00Z", cloudRepository.status.lastDownloadAt)
    }

    @Test
    fun listAvailableBackups_returnsNewestFirstManagedVersions() = runBlocking {
        val cloudRepository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(),
        )
        val client = FakeWebDavClient(
            listedFiles = listOf(
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T155500Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T15:55:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/backup--20260407T160000Z.json",
                    isDirectory = false,
                    lastModifiedAt = "2026-04-07T16:00:00Z",
                ),
                WebDavFileEntry(
                    remotePath = "/SteamVault/_history/ignore-me.txt",
                    isDirectory = false,
                ),
            ),
        )
        val manager = WebDavCloudBackupSyncManager(
            cloudBackupRepository = cloudRepository,
            vaultRepository = FakeVaultRepository(),
            webDavClient = client,
            nowIsoUtc = { "2026-04-07T16:05:00Z" },
        )

        val versions = manager.listAvailableBackups()

        assertEquals(2, versions.size)
        assertEquals("/SteamVault/_history/backup--20260407T160000Z.json", versions[0].remotePath)
        assertEquals("/SteamVault/_history/backup--20260407T155500Z.json", versions[1].remotePath)
    }

    @Test
    fun restoreBackup_downloadsSelectedVersionAndRestoresVault() = runBlocking {
        val backupPackage = demoBackupPackage()
        val cloudRepository = FakeCloudBackupRepository(
            configuration = demoConfiguration(),
            status = CloudBackupStatus(),
        )
        val vaultRepository = FakeVaultRepository()
        val client = FakeWebDavClient(
            downloadedPayload = LocalBackupPayloadCodec.encode(backupPackage),
        )
        val manager = WebDavCloudBackupSyncManager(
            cloudBackupRepository = cloudRepository,
            vaultRepository = vaultRepository,
            webDavClient = client,
            nowIsoUtc = { "2026-04-07T16:05:00Z" },
        )

        manager.restoreBackup(
            CloudBackupRemoteVersion(
                remotePath = "/SteamVault/_history/backup--20260407T160000Z.json",
                fileName = "backup--20260407T160000Z.json",
                uploadedAt = "2026-04-07T16:00:00Z",
            ),
        )

        assertEquals(backupPackage, vaultRepository.restoredBackupPackage)
        assertEquals(
            listOf("/SteamVault/_history/backup--20260407T160000Z.json"),
            client.downloadedPaths,
        )
        assertEquals("/SteamVault/_history/backup--20260407T160000Z.json", cloudRepository.status.remotePath)
    }

    private fun demoConfiguration(): WebDavBackupConfiguration {
        return WebDavBackupConfiguration(
            serverUrl = "https://dav.jianguoyun.com/dav",
            username = "demo@example.com",
            appPassword = "token-123",
            remotePath = "/SteamVault/backup.json",
        )
    }

    private fun demoBackupPackage(): LocalBackupPackage {
        return LocalBackupPackage(
            version = 1,
            exportedAt = "2026-04-07T15:55:00Z",
            encryptedVaultJson = JSONObject()
                .put("version", 1)
                .put("kdf_name", "wrapped-vault-key")
                .put("cipher_name", "aes-256-gcm")
                .put("salt_base64", "")
                .put("nonce_base64", "bm9uY2U=")
                .put("ciphertext_base64", "Y2lwaGVydGV4dA==")
                .toString(),
            securitySnapshot = MasterPasswordBackupSnapshot(
                masterPasswordSaltBase64 = "c2FsdA==",
                masterPasswordHashBase64 = "aGFzaA==",
                masterPasswordKdfName = "argon2id",
                masterPasswordIterations = 3,
                masterPasswordMemoryKiB = 65_536,
                masterPasswordParallelism = 2,
                masterPasswordVersion = 3,
                vaultKeySaltBase64 = "dmF1bHQtc2FsdA==",
                wrappedVaultKeyJson = JSONObject()
                    .put("version", 1)
                    .put(
                        "password_wrapped_key",
                        JSONObject()
                            .put("cipher_name", "aes-256-gcm")
                            .put("nonce_base64", "bm9uY2U=")
                            .put("ciphertext_base64", "Y2lwaGVydGV4dA=="),
                    )
                    .toString(),
            ),
        )
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

    private class PostRestoreMirrorCloudBackupRepository(
        configuration: WebDavBackupConfiguration?,
        status: CloudBackupStatus,
    ) : CloudBackupRepository {
        private val delegate = FakeCloudBackupRepository(configuration, status)
        private var locked = false
        val status: CloudBackupStatus
            get() = delegate.status

        fun lock() {
            locked = true
        }

        override suspend fun getConfiguration(): WebDavBackupConfiguration? {
            ensureUnlocked()
            return delegate.getConfiguration()
        }

        override suspend fun saveConfiguration(configuration: WebDavBackupConfiguration) {
            ensureUnlocked()
            delegate.saveConfiguration(configuration)
        }

        override suspend fun clearConfiguration() {
            ensureUnlocked()
            delegate.clearConfiguration()
        }

        override suspend fun getStatus(): CloudBackupStatus {
            return delegate.getStatus()
        }

        override suspend fun saveStatus(status: CloudBackupStatus) {
            delegate.saveStatus(status)
        }

        private fun ensureUnlocked() {
            check(!locked) { "cloud repository should not be touched after restore" }
        }
    }

    private class FakeWebDavClient(
        private val downloadedPayload: String = "",
        private val listedFiles: List<WebDavFileEntry> = emptyList(),
    ) : WebDavClient {
        val uploads = mutableListOf<UploadCall>()
        val downloadedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()

        override suspend fun uploadText(
            configuration: WebDavBackupConfiguration,
            payload: String,
            remotePath: String,
        ) {
            uploads += UploadCall(
                remotePath = remotePath,
                payload = payload,
            )
        }

        override suspend fun downloadText(
            configuration: WebDavBackupConfiguration,
            remotePath: String,
        ): String {
            downloadedPaths += remotePath
            return downloadedPayload
        }

        override suspend fun listFiles(
            configuration: WebDavBackupConfiguration,
            remoteDirectoryPath: String,
        ): List<WebDavFileEntry> {
            assertEquals("/SteamVault/_history", remoteDirectoryPath)
            return listedFiles
        }

        override suspend fun delete(
            configuration: WebDavBackupConfiguration,
            remotePath: String,
        ) {
            deletedPaths += remotePath
        }

        data class UploadCall(
            val remotePath: String,
            val payload: String,
        )
    }

    private class FakeVaultRepository : VaultRepository {
        private val backupPackage = demoStaticBackupPackage()
        private val onRestore: (() -> Unit)?
        var restoredBackupPackage: LocalBackupPackage? = null

        constructor(
            onRestore: (() -> Unit)? = null,
        ) {
            this.onRestore = onRestore
        }

        override suspend fun initializeEmptyVault() = Unit

        override suspend fun hasVault(): Boolean = true

        override suspend fun getTokens(): List<TokenRecord> = emptyList()

        override suspend fun getToken(tokenId: String): TokenRecord? = null

        override suspend fun saveImportedToken(importDraft: ImportDraft): TokenRecord {
            throw UnsupportedOperationException()
        }

        override suspend fun deleteToken(tokenId: String) = Unit

        override suspend fun exportVault(): VaultBlob {
            throw UnsupportedOperationException()
        }

        override suspend fun exportLocalBackup(): LocalBackupPackage = backupPackage

        override suspend fun restoreLocalBackup(backupPackage: LocalBackupPackage) {
            restoredBackupPackage = backupPackage
            onRestore?.invoke()
        }

        companion object {
            private fun demoStaticBackupPackage(): LocalBackupPackage {
                return LocalBackupPackage(
                    version = 1,
                    exportedAt = "2026-04-07T15:50:00Z",
                    encryptedVaultJson = JSONObject()
                        .put("version", 1)
                        .put("kdf_name", "wrapped-vault-key")
                        .put("cipher_name", "aes-256-gcm")
                        .put("salt_base64", "")
                        .put("nonce_base64", "bm9uY2U=")
                        .put("ciphertext_base64", "Y2lwaGVydGV4dA==")
                        .toString(),
                    securitySnapshot = MasterPasswordBackupSnapshot(
                        masterPasswordSaltBase64 = "c2FsdA==",
                        masterPasswordHashBase64 = "aGFzaA==",
                        masterPasswordKdfName = "argon2id",
                        masterPasswordIterations = 3,
                        masterPasswordMemoryKiB = 65_536,
                        masterPasswordParallelism = 2,
                        masterPasswordVersion = 3,
                        vaultKeySaltBase64 = "dmF1bHQtc2FsdA==",
                        wrappedVaultKeyJson = JSONObject()
                            .put("version", 1)
                            .put(
                                "password_wrapped_key",
                                JSONObject()
                                    .put("cipher_name", "aes-256-gcm")
                                    .put("nonce_base64", "bm9uY2U=")
                                    .put("ciphertext_base64", "Y2lwaGVydGV4dA=="),
                            )
                            .toString(),
                    ),
                )
            }
        }
    }
}
