package com.example.steam_vault_app.data.repository

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.importing.SteamAuthenticatorBindingContextCodec
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.EncryptedVaultJsonCodec
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.example.steam_vault_app.domain.security.VaultCryptography
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSteamAuthenticatorBindingContextRepository(
    context: Context,
    private val masterPasswordManager: MasterPasswordManager,
    private val vaultCryptography: VaultCryptography,
) : SteamAuthenticatorBindingContextRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.STEAM_ADD_ACCOUNT_PREFS,
        Context.MODE_PRIVATE,
    )

    override suspend fun getContext(): SteamAuthenticatorBindingContext? = withContext(Dispatchers.IO) {
        val encryptedContextJson = prefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_AUTHENTICATOR_BINDING_CONTEXT_JSON,
            null,
        ) ?: return@withContext null

        withActiveVaultKey { vaultKey ->
            val encryptedVault = EncryptedVaultJsonCodec.decode(encryptedContextJson)
            val cleartext = vaultCryptography.decryptVault(encryptedVault, vaultKey)
            try {
                SteamAuthenticatorBindingContextCodec.decode(
                    cleartext.toString(StandardCharsets.UTF_8),
                )
            } finally {
                cleartext.fill(0)
            }
        }
    }

    override suspend fun saveContext(context: SteamAuthenticatorBindingContext) = withContext(Dispatchers.IO) {
        withActiveVaultKey { vaultKey ->
            val cleartext = SteamAuthenticatorBindingContextCodec.encode(context)
                .toByteArray(StandardCharsets.UTF_8)
            try {
                val encryptedContext = vaultCryptography.encryptVault(cleartext, vaultKey)
                val saved = prefs.edit()
                    .putString(
                        SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_AUTHENTICATOR_BINDING_CONTEXT_JSON,
                        EncryptedVaultJsonCodec.encode(encryptedContext),
                    )
                    .commit()
                if (!saved) {
                    throw IllegalStateException(
                        appContext.getString(
                            R.string.repository_steam_authenticator_binding_context_save_failed,
                        ),
                    )
                }
            } finally {
                cleartext.fill(0)
            }
        }
    }

    override suspend fun clearContext() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_AUTHENTICATOR_BINDING_CONTEXT_JSON)
            .commit()
        Unit
    }

    private suspend fun <T> withActiveVaultKey(block: suspend (ByteArray) -> T): T {
        val vaultKey = masterPasswordManager.getActiveVaultKeyMaterial()
            ?: throw IllegalStateException(
                appContext.getString(R.string.repository_steam_authenticator_draft_unlock_required),
            )
        return try {
            block(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }
}
