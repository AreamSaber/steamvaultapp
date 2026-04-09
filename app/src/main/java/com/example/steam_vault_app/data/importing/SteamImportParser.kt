package com.example.steam_vault_app.data.importing

import android.content.Context
import android.net.Uri
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.security.SteamSecretCodec
import com.example.steam_vault_app.domain.model.ImportDraft
import com.example.steam_vault_app.domain.model.ImportSource
import org.json.JSONObject

class SteamImportParser(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun parse(
        rawPayload: String,
        manualAccountName: String,
        manualSharedSecret: String,
    ): ImportDraft {
        val trimmedPayload = rawPayload.trim()

        return when {
            trimmedPayload.isNotEmpty() && trimmedPayload.startsWith("{") -> parseJsonPayload(trimmedPayload)
            trimmedPayload.isNotEmpty() && trimmedPayload.startsWith("otpauth://") -> parseUriPayload(trimmedPayload)
            manualAccountName.isNotBlank() && manualSharedSecret.isNotBlank() -> ImportDraft(
                source = ImportSource.MANUAL_ENTRY,
                accountName = manualAccountName.trim(),
                sharedSecret = normalizeSharedSecret(manualSharedSecret),
                rawPayload = trimmedPayload.ifBlank { null },
            )

            else -> throw IllegalArgumentException(string(R.string.import_missing_input_detail))
        }
    }

    private fun isMaFile(json: JSONObject): Boolean {
        return json.has("shared_secret") &&
            (json.has("serial_number") || json.has("Session") || json.has("identity_secret"))
    }

    private fun parseJsonPayload(rawJson: String): ImportDraft {
        val json = JSONObject(rawJson)

        if (isMaFile(json)) {
            return parseMaFilePayload(json, rawJson)
        }

        val uri = json.optString("uri").ifBlank { null }
        val accountName = json.optString("account_name")
            .ifBlank { json.optString("accountName") }
            .ifBlank { uri?.let(::extractAccountNameFromUri).orEmpty() }
        val sharedSecret = json.optString("shared_secret")
            .ifBlank { json.optString("sharedSecret") }
            .ifBlank { uri?.let(::extractSharedSecretFromUri).orEmpty() }

        require(accountName.isNotBlank()) { string(R.string.import_json_missing_account_name) }
        require(sharedSecret.isNotBlank()) { string(R.string.import_json_missing_shared_secret) }

        return ImportDraft(
            source = ImportSource.JSON_PASTE,
            accountName = accountName,
            sharedSecret = normalizeSharedSecret(sharedSecret),
            identitySecret = json.optString("identity_secret").ifBlank { null },
            serialNumber = json.optString("serial_number").ifBlank { null },
            revocationCode = json.optString("revocation_code").ifBlank { null },
            secret1 = json.optString("secret_1").ifBlank { null },
            deviceId = json.optString("device_id").ifBlank { null },
            tokenGid = json.optString("token_gid").ifBlank { null },
            uri = uri,
            rawPayload = rawJson,
            importedSession = SteamImportedSessionParser.parseMaFileSession(json),
        )
    }

    private fun parseMaFilePayload(json: JSONObject, rawJson: String): ImportDraft {
        val sharedSecret = json.optString("shared_secret").ifBlank { null }
        require(!sharedSecret.isNullOrBlank()) { string(R.string.import_json_missing_shared_secret) }
        val importedSession = SteamImportedSessionParser.parseMaFileSession(json)

        val accountName = json.optString("account_name")
            .ifBlank { json.optString("accountName") }
            .ifBlank {
                json.optJSONObject("Session")
                    ?.optString("AccountName")
                    .orEmpty()
            }
            .ifBlank {
                json.optJSONObject("session")
                    ?.optString("AccountName")
                    .orEmpty()
            }
            .ifBlank {
                importedSession?.steamId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { steamId -> "Steam $steamId" }
                    .orEmpty()
            }

        require(accountName.isNotBlank()) { string(R.string.import_json_missing_account_name) }

        return ImportDraft(
            source = ImportSource.MAFILE,
            accountName = accountName,
            sharedSecret = normalizeSharedSecret(sharedSecret),
            identitySecret = json.optString("identity_secret").ifBlank { null },
            serialNumber = json.optString("serial_number").ifBlank { null },
            revocationCode = json.optString("revocation_code").ifBlank { null },
            secret1 = json.optString("secret_1").ifBlank { null },
            deviceId = json.optString("device_id").ifBlank { null },
            tokenGid = json.optString("token_gid").ifBlank { null },
            uri = json.optString("uri").ifBlank { null },
            rawPayload = rawJson,
            importedSession = importedSession,
        )
    }

    private fun parseUriPayload(rawUri: String): ImportDraft {
        val uri = Uri.parse(rawUri)
        val accountName = extractAccountNameFromUri(rawUri)
        val sharedSecret = extractSharedSecretFromUri(rawUri)

        require(accountName.isNotBlank()) { string(R.string.import_uri_missing_label) }
        require(sharedSecret.isNotBlank()) { string(R.string.import_uri_missing_secret) }

        return ImportDraft(
            source = ImportSource.QR_SCAN,
            accountName = accountName,
            sharedSecret = normalizeSharedSecret(sharedSecret),
            uri = uri.toString(),
            rawPayload = rawUri,
        )
    }

    private fun normalizeSharedSecret(rawSecret: String): String {
        return try {
            SteamSecretCodec.normalizeToBase64(rawSecret)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(string(R.string.import_invalid_shared_secret))
        }
    }

    private fun extractAccountNameFromUri(rawUri: String): String {
        val uri = Uri.parse(rawUri)
        val label = uri.schemeSpecificPart
            ?.substringAfter('/', "")
            ?.substringBefore('?')
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()

        return label.ifBlank {
            uri.getQueryParameter("account")?.trim().orEmpty()
        }
    }

    private fun extractSharedSecretFromUri(rawUri: String): String {
        val uri = Uri.parse(rawUri)
        return uri.getQueryParameter("secret")
            ?.takeIf { it.isNotBlank() }
            ?: uri.getQueryParameter("shared_secret")
                ?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun string(resId: Int, vararg formatArgs: Any): String {
        return appContext.getString(resId, *formatArgs)
    }
}
