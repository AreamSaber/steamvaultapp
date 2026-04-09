package com.example.steam_vault_app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.LocalMasterPasswordManager
import com.example.steam_vault_app.data.security.LocalVaultCryptography
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCloudBackupRepositoryTest {
    private lateinit var appContext: Context
    private lateinit var masterPasswordManager: LocalMasterPasswordManager
    private lateinit var repository: LocalCloudBackupRepository

    @Before
    fun setUp() = runBlocking {
        appContext = ApplicationProvider.getApplicationContext()
        clearAllPrefs()
        createUnlockedRepository("VaultPass123")
    }

    @After
    fun tearDown() = runBlocking {
        masterPasswordManager.clearUnlockedSession()
        clearAllPrefs()
    }

    @Test
    fun saveConfiguration_thenReadConfigurationAndStatus_succeeds() = runBlocking {
        val configuration = demoConfiguration()

        repository.saveConfiguration(configuration)

        assertEquals(configuration.normalized(), repository.getConfiguration())
        assertEquals(CloudBackupSyncState.IDLE, repository.getStatus().syncState)
        assertEquals(configuration.username, repository.getStatus().accountLabel)
        assertEquals(configuration.remotePath, repository.getStatus().remotePath)
    }

    @Test
    fun saveConfiguration_overwritesProfileEncryptedByOldVaultKey() = runBlocking {
        repository.saveConfiguration(
            demoConfiguration(
                username = "old@example.com",
                remotePath = "/SteamVault/old.json",
            ),
        )
        masterPasswordManager.clearUnlockedSession()
        clearSecurityPrefs()
        createUnlockedRepository("VaultPass456")

        val replacement = demoConfiguration(
            username = "new@example.com",
            remotePath = "/SteamVault/new.json",
        )

        repository.saveConfiguration(replacement)

        assertEquals(replacement.normalized(), repository.getConfiguration())
        assertEquals(replacement.username, repository.getStatus().accountLabel)
        assertEquals(replacement.remotePath, repository.getStatus().remotePath)
    }

    @Test
    fun saveConfiguration_persistsBackgroundSnapshotAndStatusMirrorForLockedReads() = runBlocking {
        val configuration = demoConfiguration()

        repository.saveConfiguration(configuration)
        masterPasswordManager.clearUnlockedSession()

        val backgroundConfiguration = repository.getBackgroundSyncConfiguration()
        val mirroredStatus = repository.getStatus()
        val lockedConfigurationRead = runCatching { repository.getConfiguration() }

        assertEquals(configuration.normalized(), backgroundConfiguration)
        assertEquals(CloudBackupSyncState.IDLE, mirroredStatus.syncState)
        assertEquals(configuration.username, mirroredStatus.accountLabel)
        assertEquals(configuration.remotePath, mirroredStatus.remotePath)
        assertTrue(lockedConfigurationRead.isFailure)
    }

    @Test
    fun clearConfiguration_clearsBackgroundSnapshotAndStatusMirror() = runBlocking {
        repository.saveConfiguration(demoConfiguration())

        repository.clearConfiguration()

        assertNull(repository.getBackgroundSyncConfiguration())
        assertEquals(CloudBackupSyncState.NOT_CONFIGURED, repository.getStatus().syncState)
    }

    private suspend fun createUnlockedRepository(password: String) {
        masterPasswordManager = LocalMasterPasswordManager(appContext)
        masterPasswordManager.createMasterPassword(password.toCharArray())
        repository = LocalCloudBackupRepository(
            context = appContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = LocalVaultCryptography(),
        )
    }

    private fun demoConfiguration(
        username: String = "demo@example.com",
        remotePath: String = "/SteamVault/backup.json",
    ): WebDavBackupConfiguration {
        return WebDavBackupConfiguration(
            serverUrl = "https://dav.jianguoyun.com/dav/",
            username = username,
            appPassword = "secret-token",
            remotePath = remotePath,
        )
    }

    private fun clearSecurityPrefs() {
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.SECURITY_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private fun clearAllPrefs() {
        clearSecurityPrefs()
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.CLOUD_BACKUP_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
