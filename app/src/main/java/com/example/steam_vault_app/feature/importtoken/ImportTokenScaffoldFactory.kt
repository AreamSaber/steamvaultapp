package com.example.steam_vault_app.feature.importtoken

import org.json.JSONObject

internal object ImportTokenScaffoldFactory {
    fun buildExistingAuthenticatorJson(
        entryContext: ImportTokenEntryContext,
    ): String? {
        if (entryContext.kind != ImportTokenEntryContext.Kind.EXISTING_AUTHENTICATOR) {
            return null
        }

        val accountName = entryContext.preferredAccountName ?: return null
        val fields = buildList {
            add("account_name" to accountName)
            entryContext.steamId?.let { steamId ->
                add("steamid" to steamId)
            }
            entryContext.deviceId?.let { deviceId ->
                add("device_id" to deviceId)
            }
            add("shared_secret" to "PASTE_SHARED_SECRET_HERE")
            add("identity_secret" to "PASTE_IDENTITY_SECRET_HERE")
            add("serial_number" to "")
            add("revocation_code" to "")
            add("token_gid" to "")
        }

        return buildString {
            appendLine("{")
            fields.forEachIndexed { index, (name, value) ->
                append("  ")
                append(JSONObject.quote(name))
                append(": ")
                append(JSONObject.quote(value))
                if (index < fields.lastIndex) {
                    append(',')
                }
                appendLine()
            }
            append('}')
        }
    }
}
