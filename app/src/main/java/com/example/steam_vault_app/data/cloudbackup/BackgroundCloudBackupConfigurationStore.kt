package com.example.steam_vault_app.data.cloudbackup

import android.content.Context
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.AndroidKeystoreBlobProtector
import com.example.steam_vault_app.data.security.KeystoreEncryptedPayload
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal class BackgroundCloudBackupConfigurationStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        SteamVaultPreferenceKeys.CLOUD_BACKUP_PREFS,
        Context.MODE_PRIVATE,
    )
    private val protector = AndroidKeystoreBlobProtector(KEY_ALIAS)

    fun save(configuration: WebDavBackupConfiguration): Boolean {
        val cleartext = JSONObject()
            .put("server_url", configuration.serverUrl)
            .put("username", configuration.username)
            .put("app_password", configuration.appPassword)
            .put("remote_path", configuration.remotePath)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        try {
            val encrypted = protector.encrypt(cleartext)
            return prefs.edit()
                .putString(
                    SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON,
                    encodeEncryptedPayload(encrypted),
                )
                .commit()
        } finally {
            cleartext.fill(0)
        }
    }

    fun readOrNull(): WebDavBackupConfiguration? {
        val encryptedJson = prefs.getString(
            SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON,
            null,
        ) ?: return null
        val cleartext = protector.decrypt(decodeEncryptedPayload(encryptedJson))
        return try {
            val json = JSONObject(cleartext.toString(StandardCharsets.UTF_8))
            WebDavBackupConfiguration(
                serverUrl = json.getString("server_url"),
                username = json.getString("username"),
                appPassword = json.getString("app_password"),
                remotePath = json.getString("remote_path"),
            ).normalized()
        } finally {
            cleartext.fill(0)
        }
    }

    fun clear(): Boolean {
        return prefs.edit()
            .remove(SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON)
            .commit()
    }

    private companion object {
        private const val KEY_ALIAS = "steam_vault_background_cloud_backup_config"

        private fun encodeEncryptedPayload(payload: KeystoreEncryptedPayload): String {
            return JSONObject()
                .put("cipher_name", payload.cipherName)
                .put("nonce_base64", payload.nonceBase64)
                .put("ciphertext_base64", payload.ciphertextBase64)
                .toString()
        }

        private fun decodeEncryptedPayload(rawPayload: String): KeystoreEncryptedPayload {
            val json = JSONObject(rawPayload)
            return KeystoreEncryptedPayload(
                cipherName = json.getString("cipher_name"),
                nonceBase64 = json.getString("nonce_base64"),
                ciphertextBase64 = json.getString("ciphertext_base64"),
            )
        }
    }
}
