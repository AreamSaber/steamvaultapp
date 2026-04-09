package com.example.steam_vault_app.data.repository

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.EncryptedVaultJsonCodec
import com.example.steam_vault_app.data.steam.SteamGuardDataProfile
import com.example.steam_vault_app.data.steam.SteamGuardDataProfileCodec
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.SteamGuardDataRecord
import com.example.steam_vault_app.domain.repository.SteamGuardDataRepository
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.example.steam_vault_app.domain.security.VaultCryptography
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSteamGuardDataRepository(
    context: Context,
    private val masterPasswordManager: MasterPasswordManager,
    private val vaultCryptography: VaultCryptography,
    private val autoCloudBackupScheduler: AutoCloudBackupScheduler? = null,
) : SteamGuardDataRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.STEAM_PROTOCOL_LOGIN_PREFS,
        Context.MODE_PRIVATE,
    )

    override suspend fun getGuardData(
        accountName: String?,
        steamId: String?,
    ): String? = withContext(Dispatchers.IO) {
        readProfileOrNull()
            ?.records
            ?.findBestMatch(accountName = accountName, steamId = steamId)
            ?.guardData
    }

    override suspend fun saveGuardData(record: SteamGuardDataRecord): Unit = withContext(Dispatchers.IO) {
        val previousRecords = readProfileOrNull()?.records.orEmpty()
        persistProfile(
            SteamGuardDataProfile(
                records = previousRecords.filterNot {
                    it.matches(accountName = record.accountName, steamId = record.steamId)
                } + record,
            ),
        )
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        Unit
    }

    override suspend fun clearGuardData(
        accountName: String?,
        steamId: String?,
    ): Unit = withContext(Dispatchers.IO) {
        if (accountName.isNullOrBlank() && steamId.isNullOrBlank()) {
            return@withContext Unit
        }

        val remainingRecords = readProfileOrNull()
            ?.records
            .orEmpty()
            .filterNot { it.matches(accountName = accountName, steamId = steamId) }

        if (remainingRecords.isEmpty()) {
            prefs.edit()
                .remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON)
                .commit()
            autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
            return@withContext Unit
        }

        persistProfile(SteamGuardDataProfile(remainingRecords))
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        Unit
    }

    private suspend fun readProfileOrNull(): SteamGuardDataProfile? {
        val encryptedProfileJson = prefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON,
            null,
        ) ?: return null

        return withActiveVaultKey { vaultKey ->
            val encryptedVault = EncryptedVaultJsonCodec.decode(encryptedProfileJson)
            val cleartext = vaultCryptography.decryptVault(encryptedVault, vaultKey)
            try {
                SteamGuardDataProfileCodec.decode(cleartext.toString(StandardCharsets.UTF_8))
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun persistProfile(profile: SteamGuardDataProfile) {
        withActiveVaultKey { vaultKey ->
            val cleartext = SteamGuardDataProfileCodec.encode(profile).toByteArray(StandardCharsets.UTF_8)
            try {
                val encryptedProfile = vaultCryptography.encryptVault(cleartext, vaultKey)
                val saved = prefs.edit()
                    .putString(
                        SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON,
                        EncryptedVaultJsonCodec.encode(encryptedProfile),
                    )
                    .commit()

                if (!saved) {
                    throw IllegalStateException(
                        appContext.getString(R.string.repository_steam_session_save_failed),
                    )
                }
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun <T> withActiveVaultKey(block: suspend (ByteArray) -> T): T {
        val vaultKey = masterPasswordManager.getActiveVaultKeyMaterial()
            ?: throw IllegalStateException(
                appContext.getString(R.string.repository_steam_session_unlock_required),
            )
        return try {
            block(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }

    private fun List<SteamGuardDataRecord>.findBestMatch(
        accountName: String?,
        steamId: String?,
    ): SteamGuardDataRecord? {
        val normalizedSteamId = steamId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAccountName = accountName?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            normalizedSteamId != null -> lastOrNull { it.steamId == normalizedSteamId }
                ?: normalizedAccountName?.let { name ->
                    lastOrNull { existing -> existing.matches(accountName = name, steamId = null) }
                }

            normalizedAccountName != null -> lastOrNull {
                it.matches(accountName = normalizedAccountName, steamId = null)
            }

            else -> null
        }
    }

    private fun SteamGuardDataRecord.matches(
        accountName: String?,
        steamId: String?,
    ): Boolean {
        val normalizedSteamId = steamId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedSteamId != null && this.steamId == normalizedSteamId) {
            return true
        }

        val normalizedAccountName = accountName?.trim()?.takeIf { it.isNotEmpty() }
        return normalizedAccountName != null &&
            this.accountName?.trim()?.equals(normalizedAccountName, ignoreCase = true) == true
    }
}
