package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.sync.SteamSessionValidationSyncManager
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DefaultSteamSessionValidationSyncManager(
    private val steamSessionRepository: SteamSessionRepository,
    private val apiClient: SteamSessionValidationApiClient,
    private val nowIsoUtc: () -> String = { Instant.now().toString() },
    context: Context? = null,
) : SteamSessionValidationSyncManager {
    private val messages = Messages.fromContext(context)

    override suspend fun validateSession(tokenId: String): SteamSessionRecord = withContext(Dispatchers.IO) {
        val session = steamSessionRepository.getSession(tokenId)
            ?: throw IllegalStateException(messages.sessionRequired)
        val normalizedCookies = normalizeCookies(session)
        if (!normalizedCookies.hasCookie("sessionid")) {
            throw IllegalStateException(messages.sessionCookieRequired)
        }
        if (!normalizedCookies.hasCookie("steamLoginSecure") && !normalizedCookies.hasCookie("steamLogin")) {
            throw IllegalStateException(messages.loginCookieRequired)
        }

        val validatedAt = nowIsoUtc()
        try {
            val result = apiClient.validateSession(
                SteamSessionValidationRequest(
                    cookies = normalizedCookies,
                    steamIdHint = session.steamId,
                ),
            )
            val updatedSession = session.copy(
                steamId = result.resolvedSteamId ?: session.steamId,
                sessionId = normalizedCookies.firstOrNull {
                    it.name.equals("sessionid", ignoreCase = true)
                }?.value ?: session.sessionId,
                cookies = normalizedCookies,
                updatedAt = validatedAt,
                lastValidatedAt = validatedAt,
                validationStatus = SteamSessionValidationStatus.SUCCESS,
                lastValidationErrorMessage = null,
            )
            steamSessionRepository.saveSession(updatedSession)
            updatedSession
        } catch (error: Exception) {
            val message = error.message ?: messages.validationFailed
            val failedSession = session.copy(
                sessionId = normalizedCookies.firstOrNull {
                    it.name.equals("sessionid", ignoreCase = true)
                }?.value ?: session.sessionId,
                cookies = normalizedCookies,
                updatedAt = validatedAt,
                lastValidatedAt = validatedAt,
                validationStatus = SteamSessionValidationStatus.ERROR,
                lastValidationErrorMessage = message,
            )
            steamSessionRepository.saveSession(failedSession)
            throw IllegalStateException(message, error)
        }
    }

    private fun normalizeCookies(session: SteamSessionRecord): List<SteamSessionCookie> {
        val cookies = session.cookies
            .filter { it.name.isNotBlank() && it.value.isNotBlank() }
            .toMutableList()
        val sessionId = session.sessionId?.trim().orEmpty()
        if (sessionId.isNotBlank() && !cookies.hasCookie("sessionid")) {
            cookies += SteamSessionCookie(name = "sessionid", value = sessionId)
        }
        return cookies
    }

    private fun List<SteamSessionCookie>.hasCookie(name: String): Boolean {
        return any { it.name.equals(name, ignoreCase = true) && it.value.isNotBlank() }
    }

    private data class Messages(
        val sessionRequired: String,
        val sessionCookieRequired: String,
        val loginCookieRequired: String,
        val validationFailed: String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        sessionRequired = "Steam session is required before validation.",
                        sessionCookieRequired = "sessionid cookie is required before validation.",
                        loginCookieRequired = "steamLoginSecure cookie is required before validation.",
                        validationFailed = "Unable to validate Steam session.",
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    sessionRequired = appContext.getString(R.string.steam_session_validation_session_required),
                    sessionCookieRequired = appContext.getString(R.string.steam_session_validation_session_cookie_required),
                    loginCookieRequired = appContext.getString(R.string.steam_session_validation_login_cookie_required),
                    validationFailed = appContext.getString(R.string.steam_session_validation_failed),
                )
            }
        }
    }
}
