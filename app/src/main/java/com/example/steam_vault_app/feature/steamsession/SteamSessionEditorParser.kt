package com.example.steam_vault_app.feature.steamsession

import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import java.time.Instant

internal enum class SteamSessionEditorError {
    SESSION_MATERIAL_MISSING,
    COOKIE_MALFORMED,
    COOKIE_NAME_MISSING,
    COOKIE_VALUE_MISSING,
}

internal class SteamSessionEditorException(
    val error: SteamSessionEditorError,
    val lineNumber: Int? = null,
) : IllegalArgumentException(error.name)

internal object SteamSessionEditorParser {
    fun buildSessionRecord(
        tokenId: String,
        accountName: String,
        existingSession: SteamSessionRecord?,
        steamIdInput: String,
        sessionIdInput: String,
        oauthTokenInput: String,
        rawCookiesInput: String,
        nowProvider: () -> String = { Instant.now().toString() },
    ): SteamSessionRecord {
        val steamId = steamIdInput.trim().ifEmpty { null }
        val sessionId = sessionIdInput.trim().ifEmpty { null }
        val oauthToken = oauthTokenInput.trim().ifEmpty { null }
        val cookies = mergeSessionIdCookie(
            sessionId = sessionId,
            cookies = parseCookies(rawCookiesInput),
        )

        if (sessionId == null && oauthToken == null && cookies.isEmpty()) {
            throw SteamSessionEditorException(SteamSessionEditorError.SESSION_MATERIAL_MISSING)
        }

        val now = nowProvider()
        return SteamSessionRecord(
            tokenId = tokenId,
            accountName = accountName,
            steamId = steamId,
            sessionId = sessionId,
            cookies = cookies,
            oauthToken = oauthToken,
            createdAt = existingSession?.createdAt ?: now,
            updatedAt = now,
            lastValidatedAt = null,
            validationStatus = SteamSessionValidationStatus.UNKNOWN,
            lastValidationErrorMessage = null,
        )
    }

    fun formatCookies(cookies: List<SteamSessionCookie>): String {
        return cookies.joinToString(separator = "\n") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    }

    internal fun parseCookies(rawCookiesInput: String): List<SteamSessionCookie> {
        return rawCookiesInput.lineSequence()
            .mapIndexedNotNull { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    return@mapIndexedNotNull null
                }

                val separatorIndex = line.indexOf('=')
                if (separatorIndex < 0) {
                    throw SteamSessionEditorException(
                        error = SteamSessionEditorError.COOKIE_MALFORMED,
                        lineNumber = index + 1,
                    )
                }

                val name = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim()

                if (name.isEmpty()) {
                    throw SteamSessionEditorException(
                        error = SteamSessionEditorError.COOKIE_NAME_MISSING,
                        lineNumber = index + 1,
                    )
                }
                if (value.isEmpty()) {
                    throw SteamSessionEditorException(
                        error = SteamSessionEditorError.COOKIE_VALUE_MISSING,
                        lineNumber = index + 1,
                    )
                }

                SteamSessionCookie(name = name, value = value)
            }
            .toList()
    }

    private fun mergeSessionIdCookie(
        sessionId: String?,
        cookies: List<SteamSessionCookie>,
    ): List<SteamSessionCookie> {
        if (sessionId.isNullOrBlank()) {
            return cookies
        }

        return cookies
            .filterNot { it.name.equals("sessionid", ignoreCase = true) } +
            SteamSessionCookie(name = "sessionid", value = sessionId)
    }
}
