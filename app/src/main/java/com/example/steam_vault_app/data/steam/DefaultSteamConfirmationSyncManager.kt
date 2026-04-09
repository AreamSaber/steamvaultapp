package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.model.SteamConfirmation
import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.SteamTimeRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.sync.SteamConfirmationSyncManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DefaultSteamConfirmationSyncManager(
    private val vaultRepository: VaultRepository,
    private val steamSessionRepository: SteamSessionRepository,
    private val steamTimeRepository: SteamTimeRepository,
    private val apiClient: SteamConfirmationApiClient,
    private val steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator? = null,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    context: Context? = null,
) : SteamConfirmationSyncManager {
    private val messages = Messages.fromContext(context)

    override suspend fun fetchConfirmations(tokenId: String): List<SteamConfirmation> = withContext(Dispatchers.IO) {
        executeWithSessionRecovery(tokenId) {
            apiClient.fetchConfirmations(buildRequest(tokenId = tokenId, tag = TAG_FETCH))
        }
    }

    override suspend fun approveConfirmation(
        tokenId: String,
        confirmationId: String,
        confirmationNonce: String,
    ): List<SteamConfirmation> = resolveAndRefresh(
        tokenId = tokenId,
        confirmationId = confirmationId,
        confirmationNonce = confirmationNonce,
        approve = true,
    )

    override suspend fun rejectConfirmation(
        tokenId: String,
        confirmationId: String,
        confirmationNonce: String,
    ): List<SteamConfirmation> = resolveAndRefresh(
        tokenId = tokenId,
        confirmationId = confirmationId,
        confirmationNonce = confirmationNonce,
        approve = false,
    )

    private suspend fun resolveAndRefresh(
        tokenId: String,
        confirmationId: String,
        confirmationNonce: String,
        approve: Boolean,
    ): List<SteamConfirmation> = withContext(Dispatchers.IO) {
        require(confirmationId.isNotBlank()) { messages.confirmationIdMissing }
        require(confirmationNonce.isNotBlank()) { messages.confirmationNonceMissing }

        executeWithSessionRecovery(tokenId) {
            val actionTag = if (approve) TAG_APPROVE else TAG_REJECT
            apiClient.resolveConfirmation(
                request = buildRequest(tokenId = tokenId, tag = actionTag),
                confirmationId = confirmationId,
                confirmationNonce = confirmationNonce,
                approve = approve,
            )
            apiClient.fetchConfirmations(buildRequest(tokenId = tokenId, tag = TAG_FETCH))
        }
    }

    private suspend fun buildRequest(
        tokenId: String,
        tag: String,
    ): SteamConfirmationRequest {
        val token = vaultRepository.getToken(tokenId)
            ?: throw IllegalStateException(messages.tokenNotFound)
        val session = resolveSessionForConfirmations(tokenId = tokenId, token = token)
            ?: throw IllegalStateException(messages.sessionRequired)
        val normalizedCookies = normalizeCookies(session)
        val steamId = session.steamId
            ?.takeIf { it.isNotBlank() }
            ?: deriveSteamIdFromCookies(normalizedCookies)
            ?: throw IllegalStateException(messages.steamIdRequired)
        val identitySecret = token.identitySecret
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(messages.identitySecretRequired)
        val deviceId = token.deviceId
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(messages.deviceIdRequired)
        if (steamTimeRepository.getState().lastSyncAt == null) {
            throw IllegalStateException(messages.timeSyncRequired)
        }
        if (!normalizedCookies.hasCookie("sessionid")) {
            throw IllegalStateException(messages.sessionCookieRequired)
        }
        if (!normalizedCookies.hasCookie("steamLoginSecure") && !normalizedCookies.hasCookie("steamLogin")) {
            throw IllegalStateException(messages.loginCookieRequired)
        }

        val timestampSeconds = currentEpochSeconds() + steamTimeRepository.getState().offsetSeconds
        return SteamConfirmationRequest(
            steamId = steamId,
            deviceId = deviceId,
            cookies = normalizedCookies,
            timestampSeconds = timestampSeconds,
            confirmationKey = SteamConfirmationSigner.generateConfirmationKey(
                identitySecret = identitySecret,
                timestampSeconds = timestampSeconds,
                tag = tag,
            ),
            tag = tag,
        )
    }

    private suspend fun <T> executeWithSessionRecovery(
        tokenId: String,
        block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (error: IllegalStateException) {
            if (!looksRecoverable(error.message)) {
                throw error
            }
            val recovered = refreshSessionForConfirmations(tokenId)
            if (!recovered) {
                throw error
            }
            block()
        }
    }

    private suspend fun resolveSessionForConfirmations(
        tokenId: String,
        token: TokenRecord,
    ): SteamSessionRecord? {
        val session = steamSessionRepository.getSession(tokenId) ?: return null
        val normalizedCookies = normalizeCookies(session)
        if (
            normalizedCookies.hasCookie("sessionid") &&
            (normalizedCookies.hasCookie("steamLoginSecure") || normalizedCookies.hasCookie("steamLogin"))
        ) {
            return session
        }

        return if (refreshSessionForConfirmations(tokenId = tokenId, token = token)) {
            steamSessionRepository.getSession(tokenId) ?: session
        } else {
            session
        }
    }

    private suspend fun refreshSessionForConfirmations(
        tokenId: String,
        token: TokenRecord? = null,
    ): Boolean {
        val orchestrator = steamProtocolLoginOrchestrator ?: return false
        val existingSession = steamSessionRepository.getSession(tokenId) ?: return false
        val mobileSession = existingSession.toRefreshableMobileSession() ?: return false
        val tokenRecord = token ?: vaultRepository.getToken(tokenId) ?: return false

        val refreshedSession = orchestrator.refreshAccessToken(
            session = mobileSession,
            accountName = tokenRecord.accountName,
        )
        orchestrator.saveSessionForToken(
            tokenId = tokenId,
            accountName = tokenRecord.accountName,
            session = refreshedSession,
            updatedAt = Instant.now().toString(),
            lastValidatedAt = existingSession.lastValidatedAt,
            validationStatus = existingSession.validationStatus,
            lastValidationErrorMessage = existingSession.lastValidationErrorMessage,
        )
        return true
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

    private fun deriveSteamIdFromCookies(cookies: List<SteamSessionCookie>): String? {
        val rawCookieValue = cookies.firstOrNull {
            it.name.equals("steamLoginSecure", ignoreCase = true) ||
                it.name.equals("steamLogin", ignoreCase = true)
        }?.value ?: return null
        val decoded = URLDecoder.decode(rawCookieValue, StandardCharsets.UTF_8.name())
        return decoded.substringBefore("||").takeIf { value ->
            value.isNotBlank() && value.all(Char::isDigit)
        }
    }

    private fun List<SteamSessionCookie>.hasCookie(name: String): Boolean {
        return any { it.name.equals(name, ignoreCase = true) && it.value.isNotBlank() }
    }

    private fun SteamSessionRecord.toRefreshableMobileSession(): SteamMobileSession? {
        val accessToken = accessToken?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val refreshToken = refreshToken?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return SteamMobileSession(
            steamId = steamId?.trim().orEmpty(),
            accessToken = accessToken,
            refreshToken = refreshToken,
            guardData = guardData,
            sessionId = sessionId,
            oauthToken = oauthToken,
            cookies = cookies,
            platform = platform,
        )
    }

    private fun looksRecoverable(message: String?): Boolean {
        val normalizedMessage = message?.lowercase().orEmpty()
        return normalizedMessage.contains("expired") ||
            normalizedMessage.contains("need auth") ||
            normalizedMessage.contains("needs authentication") ||
            normalizedMessage.contains("requires authentication") ||
            normalizedMessage.contains("authentication required") ||
            normalizedMessage.contains("sessionid cookie is required") ||
            normalizedMessage.contains("steamloginsecure cookie is required") ||
            normalizedMessage.contains("steamloginsecure / steamlogin cookie is required") ||
            normalizedMessage.contains("steam session has expired")
    }

    private data class Messages(
        val tokenNotFound: String,
        val sessionRequired: String,
        val identitySecretRequired: String,
        val deviceIdRequired: String,
        val steamIdRequired: String,
        val timeSyncRequired: String,
        val sessionCookieRequired: String,
        val loginCookieRequired: String,
        val confirmationIdMissing: String,
        val confirmationNonceMissing: String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        tokenNotFound = "Token record not found.",
                        sessionRequired = "Steam session is required before loading confirmations.",
                        identitySecretRequired = "identity_secret is required before loading confirmations.",
                        deviceIdRequired = "device_id is required before loading confirmations.",
                        steamIdRequired = "SteamID is required before loading confirmations.",
                        timeSyncRequired = "Steam time must be synced before loading confirmations.",
                        sessionCookieRequired = "sessionid cookie is required before loading confirmations.",
                        loginCookieRequired = "steamLoginSecure cookie is required before loading confirmations.",
                        confirmationIdMissing = "Confirmation id cannot be blank.",
                        confirmationNonceMissing = "Confirmation nonce cannot be blank.",
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    tokenNotFound = appContext.getString(R.string.steam_confirmation_token_not_found),
                    sessionRequired = appContext.getString(R.string.steam_confirmation_session_required),
                    identitySecretRequired = appContext.getString(R.string.steam_confirmation_identity_secret_required),
                    deviceIdRequired = appContext.getString(R.string.steam_confirmation_device_id_required),
                    steamIdRequired = appContext.getString(R.string.steam_confirmation_steam_id_required),
                    timeSyncRequired = appContext.getString(R.string.steam_confirmation_time_sync_required),
                    sessionCookieRequired = appContext.getString(R.string.steam_confirmation_session_cookie_required),
                    loginCookieRequired = appContext.getString(R.string.steam_confirmation_login_cookie_required),
                    confirmationIdMissing = appContext.getString(R.string.steam_confirmation_id_required),
                    confirmationNonceMissing = appContext.getString(R.string.steam_confirmation_nonce_required),
                )
            }
        }
    }

    private companion object {
        private const val TAG_FETCH = "conf"
        private const val TAG_APPROVE = "accept"
        private const val TAG_REJECT = "reject"
    }
}
