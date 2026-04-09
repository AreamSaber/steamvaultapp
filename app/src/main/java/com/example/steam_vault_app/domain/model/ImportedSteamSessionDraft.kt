package com.example.steam_vault_app.domain.model

import java.time.Instant

data class ImportedSteamSessionDraft(
    val steamId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val sessionId: String? = null,
    val oauthToken: String? = null,
    val cookies: List<SteamSessionCookie> = emptyList(),
    val platform: SteamSessionPlatform = SteamSessionPlatform.IMPORTED_UNKNOWN,
) {
    fun toRecord(
        tokenId: String,
        accountName: String,
        existingSession: SteamSessionRecord? = null,
        updatedAt: String = Instant.now().toString(),
    ): SteamSessionRecord {
        val finalSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: existingSession?.sessionId
        val finalCookies = normalizeCookies(
            cookies = cookies,
            fallbackCookies = existingSession?.cookies.orEmpty(),
            sessionId = finalSessionId,
        )
        return SteamSessionRecord(
            tokenId = tokenId,
            accountName = accountName,
            steamId = steamId?.trim()?.takeIf { it.isNotEmpty() } ?: existingSession?.steamId,
            accessToken = accessToken?.trim()?.takeIf { it.isNotEmpty() } ?: existingSession?.accessToken,
            refreshToken = refreshToken?.trim()?.takeIf { it.isNotEmpty() } ?: existingSession?.refreshToken,
            guardData = existingSession?.guardData,
            sessionId = finalSessionId,
            cookies = finalCookies,
            oauthToken = oauthToken?.trim()?.takeIf { it.isNotEmpty() }
                ?: accessToken?.trim()?.takeIf { it.isNotEmpty() }
                ?: existingSession?.oauthToken,
            platform = platform,
            createdAt = existingSession?.createdAt ?: updatedAt,
            updatedAt = updatedAt,
            lastValidatedAt = null,
            validationStatus = SteamSessionValidationStatus.UNKNOWN,
            lastValidationErrorMessage = null,
        )
    }

    private fun normalizeCookies(
        cookies: List<SteamSessionCookie>,
        fallbackCookies: List<SteamSessionCookie>,
        sessionId: String?,
    ): List<SteamSessionCookie> {
        val source = if (cookies.isNotEmpty()) cookies else fallbackCookies
        val normalized = LinkedHashMap<String, SteamSessionCookie>()
        source.forEach { cookie ->
            val normalizedName = cookie.name.trim()
            val normalizedValue = cookie.value.trim()
            if (normalizedName.isEmpty() || normalizedValue.isEmpty()) {
                return@forEach
            }
            normalized[normalizedName.lowercase()] = SteamSessionCookie(
                name = normalizedName,
                value = normalizedValue,
            )
        }
        sessionId?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            normalized["sessionid"] = SteamSessionCookie(name = "sessionid", value = value)
        }
        return normalized.values.toList()
    }
}
