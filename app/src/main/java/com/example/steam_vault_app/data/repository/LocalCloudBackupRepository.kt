package com.example.steam_vault_app.data.repository

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.data.cloudbackup.BackgroundCloudBackupConfigurationStore
import com.example.steam_vault_app.data.cloudbackup.BackgroundSyncCloudBackupRepository
import com.example.steam_vault_app.data.cloudbackup.CloudBackupProfile
import com.example.steam_vault_app.data.cloudbackup.CloudBackupProfileCodec
import com.example.steam_vault_app.data.cloudbackup.CloudBackupStatusCodec
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.EncryptedVaultJsonCodec
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.example.steam_vault_app.domain.security.VaultCryptography
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalCloudBackupRepository(
    context: Context,
    private val masterPasswordManager: MasterPasswordManager,
    private val vaultCryptography: VaultCryptography,
    private val autoCloudBackupScheduler: AutoCloudBackupScheduler? = null,
) : BackgroundSyncCloudBackupRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.CLOUD_BACKUP_PREFS,
        Context.MODE_PRIVATE,
    )
    private val backgroundConfigurationStore = BackgroundCloudBackupConfigurationStore(appContext)

    override suspend fun getConfiguration(): WebDavBackupConfiguration? = withContext(Dispatchers.IO) {
        readProfileRecoveringUnreadableContent().configuration
    }

    override suspend fun saveConfiguration(configuration: WebDavBackupConfiguration) = withContext(Dispatchers.IO) {
        val normalizedConfiguration = configuration.normalized()
        val previousStatus = readPersistedStatusOrNull()
            ?: readProfileRecoveringUnreadableContentOrNull(swallowUnlockRequired = true)?.status
        val nextStatus = previousStatus?.copy(
            syncState = if (previousStatus.syncState == CloudBackupSyncState.NOT_CONFIGURED) {
                CloudBackupSyncState.IDLE
            } else {
                previousStatus.syncState
            },
            accountLabel = normalizedConfiguration.username,
            remotePath = normalizedConfiguration.remotePath,
            lastErrorMessage = null,
        ) ?: CloudBackupStatus(
            syncState = CloudBackupSyncState.IDLE,
            accountLabel = normalizedConfiguration.username,
            remotePath = normalizedConfiguration.remotePath,
        )

        persistEncryptedProfile(
            CloudBackupProfile(
                configuration = normalizedConfiguration,
                status = nextStatus,
            ),
        )
        persistStatusMirror(nextStatus)
        saveBackgroundConfigurationSnapshot(normalizedConfiguration)
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.CONFIGURATION_CHANGED)
        Unit
    }

    override suspend fun clearConfiguration() = withContext(Dispatchers.IO) {
        val clearedProfileAndStatus = prefs.edit()
            .remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON)
            .remove(SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON)
            .commit()
        val clearedBackgroundSnapshot = backgroundConfigurationStore.clear()
        if (!clearedProfileAndStatus || !clearedBackgroundSnapshot) {
            throw IllegalStateException(string(R.string.repository_cloud_backup_save_failed))
        }
        Unit
    }

    override suspend fun getStatus(): CloudBackupStatus = withContext(Dispatchers.IO) {
        readPersistedStatusOrNull()
            ?: readProfileRecoveringUnreadableContentOrNull(swallowUnlockRequired = true)?.status?.also(::persistStatusMirror)
            ?: CloudBackupStatus()
    }

    override suspend fun saveStatus(status: CloudBackupStatus) = withContext(Dispatchers.IO) {
        persistStatusMirror(status)
        val currentProfile = readProfileRecoveringUnreadableContentOrNull(swallowUnlockRequired = true)
            ?: return@withContext
        persistEncryptedProfile(
            currentProfile.copy(
                status = status,
            ),
        )
    }

    override suspend fun getBackgroundSyncConfiguration(): WebDavBackupConfiguration? = withContext(Dispatchers.IO) {
        backgroundConfigurationStore.readOrNull()
    }

    override suspend fun refreshBackgroundSyncConfigurationSnapshot(): Boolean = withContext(Dispatchers.IO) {
        val configuration = readProfileRecoveringUnreadableContentOrNull(
            swallowUnlockRequired = true,
        )?.configuration ?: return@withContext false
        saveBackgroundConfigurationSnapshot(configuration)
        true
    }

    override suspend fun clearBackgroundSyncConfigurationSnapshot() = withContext(Dispatchers.IO) {
        if (!backgroundConfigurationStore.clear()) {
            throw IllegalStateException(string(R.string.repository_cloud_backup_save_failed))
        }
    }

    private suspend fun readProfileRecoveringUnreadableContent(): CloudBackupProfile {
        return readProfileRecoveringUnreadableContentOrNull() ?: CloudBackupProfile(
            configuration = null,
            status = CloudBackupStatus(),
        )
    }

    // Cloud backup settings are recoverable by re-entering them, so unreadable legacy
    // blobs should not permanently block the user from saving fresh configuration.
    private suspend fun readProfileRecoveringUnreadableContentOrNull(
        swallowUnlockRequired: Boolean = false,
    ): CloudBackupProfile? {
        return try {
            readProfileOrNull()
        } catch (error: CloudBackupUnlockRequiredException) {
            if (swallowUnlockRequired) {
                null
            } else {
                throw error
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readProfileOrNull(): CloudBackupProfile? {
        val encryptedProfileJson = prefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON,
            null,
        ) ?: return null

        return withActiveVaultKey { vaultKey ->
            val encryptedVault = EncryptedVaultJsonCodec.decode(encryptedProfileJson)
            val cleartext = vaultCryptography.decryptVault(encryptedVault, vaultKey)
            try {
                CloudBackupProfileCodec.decode(cleartext.toString(StandardCharsets.UTF_8))
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun persistEncryptedProfile(profile: CloudBackupProfile) {
        withActiveVaultKey { vaultKey ->
            val cleartext = CloudBackupProfileCodec.encode(profile).toByteArray(StandardCharsets.UTF_8)
            try {
                val encryptedProfile = vaultCryptography.encryptVault(cleartext, vaultKey)
                val saved = prefs.edit()
                    .putString(
                        SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON,
                        EncryptedVaultJsonCodec.encode(encryptedProfile),
                    )
                    .commit()

                if (!saved) {
                    throw IllegalStateException(string(R.string.repository_cloud_backup_save_failed))
                }
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private fun readPersistedStatusOrNull(): CloudBackupStatus? {
        val rawStatus = prefs.getString(
            SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON,
            null,
        ) ?: return null
        return runCatching { CloudBackupStatusCodec.decode(rawStatus) }.getOrNull()
    }

    private fun persistStatusMirror(status: CloudBackupStatus) {
        val saved = prefs.edit()
            .putString(
                SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON,
                CloudBackupStatusCodec.encode(status),
            )
            .commit()

        if (!saved) {
            throw IllegalStateException(string(R.string.repository_cloud_backup_save_failed))
        }
    }

    private fun saveBackgroundConfigurationSnapshot(configuration: WebDavBackupConfiguration) {
        if (!backgroundConfigurationStore.save(configuration)) {
            throw IllegalStateException(string(R.string.repository_cloud_backup_save_failed))
        }
    }

    private suspend fun <T> withActiveVaultKey(block: suspend (ByteArray) -> T): T {
        val vaultKey = masterPasswordManager.getActiveVaultKeyMaterial()
            ?: throw CloudBackupUnlockRequiredException()
        return try {
            block(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }

    private inner class CloudBackupUnlockRequiredException : IllegalStateException(
        string(R.string.repository_cloud_backup_unlock_required),
    )

    private fun string(resId: Int, vararg formatArgs: Any): String {
        return appContext.getString(resId, *formatArgs)
    }
}
