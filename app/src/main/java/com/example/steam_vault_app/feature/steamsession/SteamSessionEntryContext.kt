package com.example.steam_vault_app.feature.steamsession

data class SteamSessionEntryContext(
    val kind: Kind,
    val suggestedSteamId: String? = null,
    val suggestedAccountName: String? = null,
) {
    enum class Kind {
        IMPORTED_EXISTING_AUTHENTICATOR,
    }

    companion object {
        fun importedExistingAuthenticator(
            steamId: String?,
            accountName: String?,
        ): SteamSessionEntryContext {
            return SteamSessionEntryContext(
                kind = Kind.IMPORTED_EXISTING_AUTHENTICATOR,
                suggestedSteamId = steamId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                suggestedAccountName = accountName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

object SteamSessionEntryContextStore {
    private var pendingTokenId: String? = null
    private var pendingEntryContext: SteamSessionEntryContext? = null

    fun push(
        tokenId: String,
        entryContext: SteamSessionEntryContext?,
    ) {
        val normalizedTokenId = tokenId.trim()
        if (normalizedTokenId.isEmpty() || entryContext == null) {
            pendingTokenId = null
            pendingEntryContext = null
            return
        }
        pendingTokenId = normalizedTokenId
        pendingEntryContext = entryContext
    }

    fun consume(tokenId: String): SteamSessionEntryContext? {
        val normalizedTokenId = tokenId.trim()
        if (normalizedTokenId.isEmpty() || pendingTokenId != normalizedTokenId) {
            return null
        }
        val current = pendingEntryContext
        pendingTokenId = null
        pendingEntryContext = null
        return current
    }
}
