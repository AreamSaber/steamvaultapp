package com.example.steam_vault_app.domain.model

data class SteamMobileSession(
    val steamId: String,
    val accessToken: String,
    val refreshToken: String,
    val guardData: String? = null,
    val sessionId: String? = null,
    val oauthToken: String? = null,
    val cookies: List<SteamSessionCookie> = emptyList(),
    val platform: SteamSessionPlatform = SteamSessionPlatform.MOBILE_APP,
) {
    fun toRecord(
        tokenId: String,
        accountName: String,
        createdAt: String,
        updatedAt: String = createdAt,
        lastValidatedAt: String? = null,
        validationStatus: SteamSessionValidationStatus = SteamSessionValidationStatus.UNKNOWN,
        lastValidationErrorMessage: String? = null,
    ): SteamSessionRecord {
        return SteamSessionRecord(
            tokenId = tokenId,
            accountName = accountName,
            steamId = steamId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            guardData = guardData,
            sessionId = sessionId,
            cookies = cookies,
            oauthToken = oauthToken,
            platform = platform,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastValidatedAt = lastValidatedAt,
            validationStatus = validationStatus,
            lastValidationErrorMessage = lastValidationErrorMessage,
        )
    }

    companion object {
        fun fromRecord(record: SteamSessionRecord): SteamMobileSession? {
            val steamId = record.steamId ?: return null
            val accessToken = record.accessToken ?: return null
            val refreshToken = record.refreshToken ?: return null

            return SteamMobileSession(
                steamId = steamId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                guardData = record.guardData,
                sessionId = record.sessionId,
                oauthToken = record.oauthToken,
                cookies = record.cookies,
                platform = record.platform,
            )
        }
    }
}
