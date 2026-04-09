package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingException
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingFailureReason

internal enum class SteamAuthenticatorBindingFailurePhase {
    BEGIN,
    FINALIZE,
    SAVE,
}

internal enum class SteamAuthenticatorBindingFailureGuidanceKind {
    SIGN_IN_AGAIN,
    CHECK_ACTIVATION_CODE,
    RETRY_LATER,
    SWITCH_AUTH_MODE,
    OPEN_COMPATIBILITY_IMPORT,
}

internal data class SteamAuthenticatorBindingFailureGuidance(
    val kind: SteamAuthenticatorBindingFailureGuidanceKind,
    val suggestedMode: SteamAuthenticatorBindingAuthMode? = null,
)

internal object SteamAuthenticatorBindingFailureGuidanceFactory {
    fun from(
        error: Throwable?,
        errorMessage: String?,
        phase: SteamAuthenticatorBindingFailurePhase,
        currentMode: SteamAuthenticatorBindingAuthMode?,
        availableModes: List<SteamAuthenticatorBindingAuthMode>,
    ): SteamAuthenticatorBindingFailureGuidance? {
        val typedReason = (error as? SteamAuthenticatorBindingException)?.reason
        val typedGuidance = typedReason?.let { reason ->
            guidanceFromReason(
                reason = reason,
                currentMode = currentMode,
                availableModes = availableModes,
            )
        }
        if (typedGuidance != null) {
            return typedGuidance
        }

        val normalized = errorMessage
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        if (looksLikeSessionExpired(normalized)) {
            return SteamAuthenticatorBindingFailureGuidance(
                kind = SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN,
            )
        }

        if (phase == SteamAuthenticatorBindingFailurePhase.FINALIZE &&
            looksLikeActivationCodeIssue(normalized)
        ) {
            return SteamAuthenticatorBindingFailureGuidance(
                kind = SteamAuthenticatorBindingFailureGuidanceKind.CHECK_ACTIVATION_CODE,
            )
        }

        if (looksLikeRetryLater(normalized)) {
            return SteamAuthenticatorBindingFailureGuidance(
                kind = SteamAuthenticatorBindingFailureGuidanceKind.RETRY_LATER,
            )
        }

        val suggestedMode = when {
            looksLikeOauthIssue(normalized) -> {
                suggestModeForOauthIssue(currentMode, availableModes)
            }

            looksLikeWebApiKeyIssue(normalized) -> {
                suggestModeForWebApiKeyIssue(currentMode, availableModes)
            }

            else -> null
        }

        return suggestedMode?.let {
            SteamAuthenticatorBindingFailureGuidance(
                kind = SteamAuthenticatorBindingFailureGuidanceKind.SWITCH_AUTH_MODE,
                suggestedMode = it,
            )
        }
    }

    private fun guidanceFromReason(
        reason: SteamAuthenticatorBindingFailureReason,
        currentMode: SteamAuthenticatorBindingAuthMode?,
        availableModes: List<SteamAuthenticatorBindingAuthMode>,
    ): SteamAuthenticatorBindingFailureGuidance? {
        return when (reason) {
            SteamAuthenticatorBindingFailureReason.SESSION_EXPIRED -> {
                SteamAuthenticatorBindingFailureGuidance(
                    kind = SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN,
                )
            }

            SteamAuthenticatorBindingFailureReason.ACTIVATION_CODE_INVALID -> {
                SteamAuthenticatorBindingFailureGuidance(
                    kind = SteamAuthenticatorBindingFailureGuidanceKind.CHECK_ACTIVATION_CODE,
                )
            }

            SteamAuthenticatorBindingFailureReason.RETRY_LATER -> {
                SteamAuthenticatorBindingFailureGuidance(
                    kind = SteamAuthenticatorBindingFailureGuidanceKind.RETRY_LATER,
                )
            }

            SteamAuthenticatorBindingFailureReason.AUTHENTICATOR_ALREADY_PRESENT -> {
                SteamAuthenticatorBindingFailureGuidance(
                    kind = SteamAuthenticatorBindingFailureGuidanceKind.OPEN_COMPATIBILITY_IMPORT,
                )
            }

            SteamAuthenticatorBindingFailureReason.OAUTH_INVALID -> {
                suggestModeForOauthIssue(
                    currentMode = currentMode,
                    availableModes = availableModes,
                )?.let { suggestedMode ->
                    SteamAuthenticatorBindingFailureGuidance(
                        kind = SteamAuthenticatorBindingFailureGuidanceKind.SWITCH_AUTH_MODE,
                        suggestedMode = suggestedMode,
                    )
                }
            }

            SteamAuthenticatorBindingFailureReason.WEB_API_KEY_INVALID -> {
                suggestModeForWebApiKeyIssue(
                    currentMode = currentMode,
                    availableModes = availableModes,
                )?.let { suggestedMode ->
                    SteamAuthenticatorBindingFailureGuidance(
                        kind = SteamAuthenticatorBindingFailureGuidanceKind.SWITCH_AUTH_MODE,
                        suggestedMode = suggestedMode,
                    )
                }
            }

            SteamAuthenticatorBindingFailureReason.PHONE_NUMBER_REQUIRED,
            SteamAuthenticatorBindingFailureReason.INVALID_RESPONSE,
            SteamAuthenticatorBindingFailureReason.UNKNOWN -> null
        }
    }

    private fun looksLikeSessionExpired(message: String): Boolean {
        return message.contains("session has expired") ||
            message.contains("log in again") ||
            message.contains("login again") ||
            message.contains("not logged in") ||
            message.contains("loginform") ||
            message.contains("access denied")
    }

    private fun looksLikeActivationCodeIssue(message: String): Boolean {
        val mentionsActivation = message.contains("activation code") || message.contains("bad activation code")
        val mentionsFailure = message.contains("invalid") ||
            message.contains("incorrect") ||
            message.contains("wrong") ||
            message.contains("bad")
        return mentionsActivation && mentionsFailure || message.contains("bad activation code")
    }

    private fun looksLikeRetryLater(message: String): Boolean {
        return message.contains("too many") ||
            message.contains("try again later") ||
            message.contains("rate limit") ||
            message.contains("rate-limited") ||
            message.contains("temporarily unavailable")
    }

    private fun looksLikeOauthIssue(message: String): Boolean {
        val mentionsOauth = message.contains("access token") || message.contains("oauth")
        val mentionsFailure = message.contains("required") ||
            message.contains("invalid") ||
            message.contains("missing") ||
            message.contains("expired") ||
            message.contains("denied")
        return mentionsOauth && mentionsFailure
    }

    private fun looksLikeWebApiKeyIssue(message: String): Boolean {
        val mentionsKey = message.contains("web api key") ||
            message.contains("api key") ||
            message.contains("x-webapi-key") ||
            message.contains("publisher key")
        val mentionsFailure = message.contains("required") ||
            message.contains("invalid") ||
            message.contains("missing") ||
            message.contains("denied") ||
            message.contains("forbidden")
        return mentionsKey && mentionsFailure
    }

    private fun suggestModeForOauthIssue(
        currentMode: SteamAuthenticatorBindingAuthMode?,
        availableModes: List<SteamAuthenticatorBindingAuthMode>,
    ): SteamAuthenticatorBindingAuthMode? {
        return when (currentMode) {
            SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY -> {
                if (SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY in availableModes) {
                    SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY
                } else if (SteamAuthenticatorBindingAuthMode.OAUTH_ONLY in availableModes) {
                    SteamAuthenticatorBindingAuthMode.OAUTH_ONLY
                } else {
                    null
                }
            }

            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY -> {
                availableModes.firstOrNull { it == SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY }
            }

            else -> null
        }
    }

    private fun suggestModeForWebApiKeyIssue(
        currentMode: SteamAuthenticatorBindingAuthMode?,
        availableModes: List<SteamAuthenticatorBindingAuthMode>,
    ): SteamAuthenticatorBindingAuthMode? {
        return when (currentMode) {
            SteamAuthenticatorBindingAuthMode.OAUTH_ONLY -> {
                if (SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY in availableModes) {
                    SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY
                } else if (SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY in availableModes) {
                    SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY
                } else {
                    null
                }
            }

            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY -> {
                availableModes.firstOrNull { it == SteamAuthenticatorBindingAuthMode.OAUTH_ONLY }
            }

            else -> null
        }
    }
}
