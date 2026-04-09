package com.example.steam_vault_app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.LocalMasterPasswordManager
import com.example.steam_vault_app.data.security.LocalVaultCryptography
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVaultRepositoryBackupSettingsTest {
    private lateinit var appContext: Context
    private lateinit var masterPasswordManager: LocalMasterPasswordManager
    private lateinit var vaultRepository: LocalVaultRepository
    private lateinit var cloudBackupRepository: LocalCloudBackupRepository
    private lateinit var securitySettingsRepository: LocalSecuritySettingsRepository
    private lateinit var autoCloudBackupScheduler: RecordingAutoCloudBackupScheduler

    @Before
    fun setUp() = runBlocking {
        appContext = ApplicationProvider.getApplicationContext()
        clearPrefs()
        masterPasswordManager = LocalMasterPasswordManager(appContext)
        masterPasswordManager.createMasterPassword("VaultPass123".toCharArray())
        autoCloudBackupScheduler = RecordingAutoCloudBackupScheduler()
        vaultRepository = LocalVaultRepository(
            context = appContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = LocalVaultCryptography(),
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        cloudBackupRepository = LocalCloudBackupRepository(
            context = appContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = LocalVaultCryptography(),
        )
        securitySettingsRepository = LocalSecuritySettingsRepository(appContext)
        vaultRepository.initializeEmptyVault()
    }

    @After
    fun tearDown() = runBlocking {
        masterPasswordManager.clearUnlockedSession()
        clearPrefs()
    }

    @Test
    fun exportAndRestoreLocalBackup_preservesCloudBackupAndSecuritySettings() = runBlocking {
        val originalCloudConfiguration = WebDavBackupConfiguration(
            serverUrl = "https://dav.jianguoyun.com/dav/",
            username = "demo@example.com",
            appPassword = "secret-token",
            remotePath = "/SteamVault/backup.json",
        )
        val originalSecuritySettings = AppSecuritySettings(
            secureScreensEnabled = false,
            biometricQuickUnlockEnabled = true,
            autoLockTimeout = AutoLockTimeoutOption.FIVE_MINUTES,
        )
        cloudBackupRepository.saveConfiguration(originalCloudConfiguration)
        securitySettingsRepository.saveSettings(originalSecuritySettings)

        val backupPackage = vaultRepository.exportLocalBackup()

        cloudBackupRepository.clearConfiguration()
        securitySettingsRepository.saveSettings(AppSecuritySettings())

        vaultRepository.restoreLocalBackup(backupPackage)
        masterPasswordManager.unlock("VaultPass123".toCharArray())

        assertEquals(
            originalCloudConfiguration.normalized(),
            cloudBackupRepository.getConfiguration(),
        )
        assertEquals(originalSecuritySettings, securitySettingsRepository.getSettings())
    }

    @Test
    fun restoreLocalBackup_fromFreshWelcomeState_restoresBackupSuccessfully() = runBlocking {
        val originalCloudConfiguration = WebDavBackupConfiguration(
            serverUrl = "https://dav.jianguoyun.com/dav/",
            username = "fresh@example.com",
            appPassword = "fresh-secret",
            remotePath = "/SteamVault/fresh-backup.json",
        )
        val originalSecuritySettings = AppSecuritySettings(
            secureScreensEnabled = false,
            biometricQuickUnlockEnabled = true,
            autoLockTimeout = AutoLockTimeoutOption.FIVE_MINUTES,
        )
        cloudBackupRepository.saveConfiguration(originalCloudConfiguration)
        securitySettingsRepository.saveSettings(originalSecuritySettings)

        val backupPackage = vaultRepository.exportLocalBackup()

        masterPasswordManager.clearUnlockedSession()
        clearPrefs()

        masterPasswordManager = LocalMasterPasswordManager(appContext)
        vaultRepository = LocalVaultRepository(
            context = appContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = LocalVaultCryptography(),
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        cloudBackupRepository = LocalCloudBackupRepository(
            context = appContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = LocalVaultCryptography(),
        )
        securitySettingsRepository = LocalSecuritySettingsRepository(appContext)

        vaultRepository.restoreLocalBackup(backupPackage)

        assertTrue(masterPasswordManager.unlock("VaultPass123".toCharArray()))
        assertEquals(
            originalCloudConfiguration.normalized(),
            cloudBackupRepository.getConfiguration(),
        )
        assertEquals(originalSecuritySettings, securitySettingsRepository.getSettings())
    }

    @Test
    fun restoreLocalBackup_cancelsPendingAutoCloudUploadsBeforeApplyingBackup() = runBlocking {
        val backupPackage = vaultRepository.exportLocalBackup()

        vaultRepository.restoreLocalBackup(backupPackage)

        assertEquals(1, autoCloudBackupScheduler.cancelCount)
    }

    private fun clearPrefs() {
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.SECURITY_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.VAULT_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.CLOUD_BACKUP_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private class RecordingAutoCloudBackupScheduler : AutoCloudBackupScheduler {
        var cancelCount: Int = 0

        override fun schedule(reason: CloudBackupAutoBackupReason) = Unit

        override suspend fun cancelPendingUploadsForManualRestore() {
            cancelCount += 1
        }
    }
}
