package com.example.steam_vault_app.domain.model

enum class SteamAuthenticatorBindingContextSource {
    PROTOCOL_LOGIN,
}

data class SteamAuthenticatorBindingContext(
    val accountName: String,
    val session: SteamMobileSession,
    val capturedAt: String,
    val webApiKey: String? = null,
    val source: SteamAuthenticatorBindingContextSource =
        SteamAuthenticatorBindingContextSource.PROTOCOL_LOGIN,
) {
    val signature: String
        get() = listOf(
            source.name,
            accountName,
            session.steamId,
            session.sessionId.orEmpty(),
            capturedAt,
        ).joinToString("|")
}
