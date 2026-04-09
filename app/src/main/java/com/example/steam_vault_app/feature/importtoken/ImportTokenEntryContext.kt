package com.example.steam_vault_app.feature.importtoken

data class ImportTokenEntryContext(
    val kind: Kind,
    val suggestedAccountName: String? = null,
    val steamId: String? = null,
    val deviceId: String? = null,
) {
    enum class Kind {
        EXISTING_AUTHENTICATOR,
    }

    val preferredAccountName: String?
        get() = suggestedAccountName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: steamId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { resolvedSteamId -> "Steam $resolvedSteamId" }

    companion object {
        fun existingAuthenticator(
            accountName: String?,
            steamId: String?,
            deviceId: String? = null,
        ): ImportTokenEntryContext {
            return ImportTokenEntryContext(
                kind = Kind.EXISTING_AUTHENTICATOR,
                suggestedAccountName = accountName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                steamId = steamId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                deviceId = deviceId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

object ImportTokenEntryContextStore {
    private var pendingEntryContext: ImportTokenEntryContext? = null

    fun push(entryContext: ImportTokenEntryContext?) {
        pendingEntryContext = entryContext
    }

    fun consume(): ImportTokenEntryContext? {
        val current = pendingEntryContext
        pendingEntryContext = null
        return current
    }
}
