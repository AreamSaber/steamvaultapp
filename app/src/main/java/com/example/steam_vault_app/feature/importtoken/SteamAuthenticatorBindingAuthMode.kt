package com.example.steam_vault_app.feature.importtoken

internal enum class SteamAuthenticatorBindingAuthMode {
    OAUTH_ONLY,
    WEB_API_KEY_ONLY,
    OAUTH_AND_WEB_API_KEY,
}

internal data class SteamAuthenticatorBindingResolvedAuth(
    val oauthToken: String? = null,
    val webApiKey: String? = null,
)

internal object SteamAuthenticatorBindingAuthModeFactory {
    fun availableModes(
        preparation: SteamAuthenticatorBindingPreparation,
    ): List<SteamAuthenticatorBindingAuthMode> = buildList {
        if (preparation.oauthToken != null) {
            add(SteamAuthenticatorBindingAuthMode.OAUTH_ONLY)
        }
        if (preparation.webApiKey != null) {
            add(SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY)
        }
        if (preparation.oauthToken != null && preparation.webApiKey != null) {
            add(SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY)
        }
    }

    fun preferredMode(
        preparation: SteamAuthenticatorBindingPreparation,
    ): SteamAuthenticatorBindingAuthMode? = when {
        preparation.oauthToken != null && preparation.webApiKey != null -> {
            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY
        }

        preparation.oauthToken != null -> SteamAuthenticatorBindingAuthMode.OAUTH_ONLY
        preparation.webApiKey != null -> SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY
        else -> null
    }

    fun resolve(
        mode: SteamAuthenticatorBindingAuthMode,
        preparation: SteamAuthenticatorBindingPreparation,
    ): SteamAuthenticatorBindingResolvedAuth {
        require(mode in availableModes(preparation)) {
            "Steam authenticator binding auth mode is not available for the current draft."
        }
        return when (mode) {
            SteamAuthenticatorBindingAuthMode.OAUTH_ONLY -> {
                SteamAuthenticatorBindingResolvedAuth(
                    oauthToken = preparation.oauthToken,
                    webApiKey = null,
                )
            }

            SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY -> {
                SteamAuthenticatorBindingResolvedAuth(
                    oauthToken = null,
                    webApiKey = preparation.webApiKey,
                )
            }

            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY -> {
                SteamAuthenticatorBindingResolvedAuth(
                    oauthToken = preparation.oauthToken,
                    webApiKey = preparation.webApiKey,
                )
            }
        }
    }
}
