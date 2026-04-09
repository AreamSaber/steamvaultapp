package com.example.steam_vault_app.domain.model

data class SteamSessionRecord(
    val tokenId: String,
    val accountName: String,
    val steamId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val guardData: String? = null,
    val sessionId: String? = null,
    val cookies: List<SteamSessionCookie> = emptyList(),
    val oauthToken: String? = null,
    val platform: SteamSessionPlatform = SteamSessionPlatform.WEB_BROWSER,
    val createdAt: String,
    val updatedAt: String,
    val lastValidatedAt: String? = null,
    val validationStatus: SteamSessionValidationStatus = SteamSessionValidationStatus.UNKNOWN,
    val lastValidationErrorMessage: String? = null,
)

data class SteamSessionCookie(
    val name: String,
    val value: String,
)

enum class SteamSessionPlatform {
    WEB_BROWSER,
    MOBILE_APP,
    IMPORTED_UNKNOWN,
}

enum class SteamSessionValidationStatus {
    UNKNOWN,
    SUCCESS,
    ERROR,
}
