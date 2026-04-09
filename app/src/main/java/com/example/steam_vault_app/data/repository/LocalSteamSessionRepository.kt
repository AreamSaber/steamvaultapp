package com.example.steam_vault_app.data.repository

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.EncryptedVaultJsonCodec
import com.example.steam_vault_app.data.steam.SteamSessionProfile
import com.example.steam_vault_app.data.steam.SteamSessionProfileCodec
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.example.steam_vault_app.domain.security.VaultCryptography
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSteamSessionRepository(
    context: Context,
    private val masterPasswordManager: MasterPasswordManager,
    private val vaultCryptography: VaultCryptography,
    private val autoCloudBackupScheduler: AutoCloudBackupScheduler? = null,
) : SteamSessionRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.STEAM_SESSION_PREFS,
        Context.MODE_PRIVATE,
    )

    override suspend fun getSession(tokenId: String): SteamSessionRecord? = withContext(Dispatchers.IO) {
        readProfileOrNull()?.sessions?.firstOrNull { it.tokenId == tokenId }
    }

    override suspend fun getSessions(): List<SteamSessionRecord> = withContext(Dispatchers.IO) {
        readProfileOrNull()?.sessions.orEmpty()
    }

    override suspend fun saveSession(session: SteamSessionRecord): Unit = withContext(Dispatchers.IO) {
        val previousSessions = readProfileOrNull()?.sessions.orEmpty()
        persistProfile(
            SteamSessionProfile(
                sessions = previousSessions.filterNot { it.tokenId == session.tokenId } + session,
            ),
        )
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        Unit
    }

    override suspend fun clearSession(tokenId: String): Unit = withContext(Dispatchers.IO) {
        val remainingSessions = readProfileOrNull()?.sessions.orEmpty()
            .filterNot { it.tokenId == tokenId }
        if (remainingSessions.isEmpty()) {
            prefs.edit()
                .remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON)
                .commit()
            autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
            return@withContext
        }

        persistProfile(SteamSessionProfile(remainingSessions))
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        Unit
    }

    override suspend fun clearAllSessions() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON)
            .commit()
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        Unit
    }

    private suspend fun readProfileOrNull(): SteamSessionProfile? {
        val encryptedProfileJson = prefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON,
            null,
        ) ?: return null

        return withActiveVaultKey { vaultKey ->
            val encryptedVault = EncryptedVaultJsonCodec.decode(encryptedProfileJson)
            val cleartext = vaultCryptography.decryptVault(encryptedVault, vaultKey)
            try {
                SteamSessionProfileCodec.decode(cleartext.toString(StandardCharsets.UTF_8))
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun persistProfile(profile: SteamSessionProfile) {
        withActiveVaultKey { vaultKey ->
            val cleartext = SteamSessionProfileCodec.encode(profile).toByteArray(StandardCharsets.UTF_8)
            try {
                val encryptedProfile = vaultCryptography.encryptVault(cleartext, vaultKey)
                val saved = prefs.edit()
                    .putString(
                        SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON,
                        EncryptedVaultJsonCodec.encode(encryptedProfile),
                    )
                    .commit()

                if (!saved) {
                    throw IllegalStateException(appContext.getString(R.string.repository_steam_session_save_failed))
                }
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun <T> withActiveVaultKey(block: suspend (ByteArray) -> T): T {
        val vaultKey = masterPasswordManager.getActiveVaultKeyMaterial()
            ?: throw IllegalStateException(appContext.getString(R.string.repository_steam_session_unlock_required))
        return try {
            block(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }
}
