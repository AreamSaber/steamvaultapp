package com.example.steam_vault_app.domain.model

data class SteamAuthenticatorEnrollmentDraft(
    val steamId: String? = null,
    val sessionId: String,
    val cookiesText: String,
    val currentUrl: String,
    val capturedAt: String,
    val oauthToken: String? = null,
    val webApiKey: String? = null,
) {
    val signature: String
        get() = listOf(
            steamId.orEmpty(),
            sessionId,
            cookiesText,
            currentUrl,
            oauthToken.orEmpty(),
        ).joinToString("|")
}
