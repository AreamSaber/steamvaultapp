package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.data.security.SteamSecretCodec
import org.json.JSONObject

internal object SteamAuthenticatorBindingResponseParser {
    fun parseBeginResult(
        responseBody: String,
        steamId: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
        phoneNumberRequiredMessage: String,
        authenticatorPresentMessage: String,
    ): SteamAuthenticatorBeginResult {
        val parsedPayload = parsePayload(
            responseBody = responseBody,
            invalidResponseMessage = invalidResponseMessage,
        )
        val payload = parsedPayload.payload
        val status = payload.optInt("status").takeIf { payload.has("status") }
        val message = extractFailureMessage(parsedPayload)
        val beginSucceeded = when {
            payload.has("success") -> payload.readBoolean("success", defaultValue = false)
            parsedPayload.root.has("success") -> parsedPayload.root.readBoolean("success", defaultValue = true)
            else -> true
        }

        val sharedSecret = payload.optString("shared_secret").takeIf { it.isNotBlank() }
        if (!beginSucceeded || sharedSecret == null) {
            val failureReason = classifyBeginFailure(status = status, message = message)
            throw SteamAuthenticatorBindingException(
                reason = failureReason,
                message = beginFailureMessage(
                    reason = failureReason,
                    message = message,
                    defaultFailureMessage = defaultFailureMessage,
                    phoneNumberRequiredMessage = phoneNumberRequiredMessage,
                    authenticatorPresentMessage = authenticatorPresentMessage,
                ),
                steamStatusCode = status,
                rawResponse = responseBody,
            )
        }

        return SteamAuthenticatorBeginResult(
            steamId = steamId,
            accountName = payload.optString("account_name").takeIf { it.isNotBlank() },
            sharedSecret = SteamSecretCodec.normalizeToBase64(sharedSecret),
            identitySecret = payload.optString("identity_secret")
                .takeIf { it.isNotBlank() }
                ?.let(SteamSecretCodec::normalizeToBase64),
            serialNumber = payload.optString("serial_number").takeIf { it.isNotBlank() },
            revocationCode = payload.optString("revocation_code").takeIf { it.isNotBlank() },
            secret1 = payload.optString("secret_1")
                .takeIf { it.isNotBlank() }
                ?.let(SteamSecretCodec::normalizeToBase64),
            tokenGid = payload.optString("token_gid").takeIf { it.isNotBlank() },
            uri = payload.optString("uri").takeIf { it.isNotBlank() },
            serverTimeSeconds = payload.optLong("server_time").takeIf { payload.has("server_time") },
            status = payload.optInt("status").takeIf { payload.has("status") },
            fullyEnrolled = payload.readBoolean("fully_enrolled"),
            rawResponse = responseBody,
        )
    }

    fun parseFinalizeResult(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ): SteamAuthenticatorFinalizeResult {
        val parsedPayload = parsePayload(
            responseBody = responseBody,
            invalidResponseMessage = invalidResponseMessage,
        )
        val payload = parsedPayload.payload
        val status = payload.optInt("status").takeIf { payload.has("status") }
        val message = extractFailureMessage(parsedPayload)
        val explicitWantMoreActivation = payload.has("want_more") &&
            payload.readBoolean("want_more")
        val wantsMoreActivation = explicitWantMoreActivation || status == 88 ||
            (
                status == 2 &&
                    !payload.has("success") &&
                    !payload.has("want_more")
                )
        val success = when {
            payload.has("success") -> payload.readBoolean("success", defaultValue = false)
            status != null -> status == 1 || wantsMoreActivation
            parsedPayload.root.has("success") -> parsedPayload.root.readBoolean("success", defaultValue = false)
            else -> true
        }
        if (!success) {
            val failureReason = classifyFinalizeFailure(status = status, message = message)
            throw SteamAuthenticatorBindingException(
                reason = failureReason,
                message = message ?: defaultFailureMessage,
                steamStatusCode = status,
                rawResponse = responseBody,
            )
        }
        return SteamAuthenticatorFinalizeResult(
            success = true,
            status = status,
            serverTimeSeconds = payload.optLong("server_time").takeIf { payload.has("server_time") },
            wantsMoreActivation = wantsMoreActivation,
            rawResponse = responseBody,
        )
    }

    private data class ParsedPayload(
        val root: JSONObject,
        val payload: JSONObject,
    )

    private fun parsePayload(
        responseBody: String,
        invalidResponseMessage: String,
    ): ParsedPayload {
        val root = try {
            JSONObject(responseBody)
        } catch (error: Exception) {
            throw SteamAuthenticatorBindingException(
                reason = SteamAuthenticatorBindingFailureReason.INVALID_RESPONSE,
                message = invalidResponseMessage,
                rawResponse = responseBody,
                cause = error,
            )
        }
        val payload = root.optJSONObject("response") ?: root
        return ParsedPayload(root = root, payload = payload)
    }

    private fun JSONObject.readBoolean(
        name: String,
        defaultValue: Boolean = false,
    ): Boolean {
        if (!has(name)) {
            return defaultValue
        }
        return when (val value = opt(name)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "1", "true", "yes" -> true
                "0", "false", "no" -> false
                else -> defaultValue
            }

            else -> defaultValue
        }
    }

    private fun extractFailureMessage(parsedPayload: ParsedPayload): String? {
        return parsedPayload.payload.optString("message")
            .takeIf { it.isNotBlank() }
            ?: parsedPayload.root.optString("message").takeIf { it.isNotBlank() }
    }

    private fun beginFailureMessage(
        reason: SteamAuthenticatorBindingFailureReason,
        message: String?,
        defaultFailureMessage: String,
        phoneNumberRequiredMessage: String,
        authenticatorPresentMessage: String,
    ): String {
        return message ?: when (reason) {
            SteamAuthenticatorBindingFailureReason.PHONE_NUMBER_REQUIRED -> {
                phoneNumberRequiredMessage
            }

            SteamAuthenticatorBindingFailureReason.AUTHENTICATOR_ALREADY_PRESENT -> {
                authenticatorPresentMessage
            }

            else -> defaultFailureMessage
        }
    }

    private fun classifyBeginFailure(
        status: Int?,
        message: String?,
    ): SteamAuthenticatorBindingFailureReason {
        return when (status) {
            2 -> SteamAuthenticatorBindingFailureReason.PHONE_NUMBER_REQUIRED
            29 -> SteamAuthenticatorBindingFailureReason.AUTHENTICATOR_ALREADY_PRESENT
            else -> classifyMessageDrivenFailure(message)
        }
    }

    private fun classifyFinalizeFailure(
        status: Int?,
        message: String?,
    ): SteamAuthenticatorBindingFailureReason {
        return when (status) {
            89 -> SteamAuthenticatorBindingFailureReason.ACTIVATION_CODE_INVALID
            else -> classifyMessageDrivenFailure(message)
        }
    }

    private fun classifyMessageDrivenFailure(
        message: String?,
    ): SteamAuthenticatorBindingFailureReason {
        val normalized = message
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return SteamAuthenticatorBindingFailureReason.UNKNOWN

        if (
            normalized.contains("session has expired") ||
            normalized.contains("log in again") ||
            normalized.contains("login again") ||
            normalized.contains("not logged in") ||
            normalized.contains("loginform") ||
            normalized.contains("access denied")
        ) {
            return SteamAuthenticatorBindingFailureReason.SESSION_EXPIRED
        }

        if (
            normalized.contains("bad activation code") ||
            (
                normalized.contains("activation code") &&
                    (
                        normalized.contains("invalid") ||
                            normalized.contains("incorrect") ||
                            normalized.contains("wrong") ||
                            normalized.contains("bad")
                        )
                )
        ) {
            return SteamAuthenticatorBindingFailureReason.ACTIVATION_CODE_INVALID
        }

        if (
            normalized.contains("too many") ||
            normalized.contains("try again later") ||
            normalized.contains("rate limit") ||
            normalized.contains("rate-limited") ||
            normalized.contains("temporarily unavailable")
        ) {
            return SteamAuthenticatorBindingFailureReason.RETRY_LATER
        }

        val mentionsOauth = normalized.contains("access token") || normalized.contains("oauth")
        if (
            mentionsOauth &&
            (
                normalized.contains("required") ||
                    normalized.contains("invalid") ||
                    normalized.contains("missing") ||
                    normalized.contains("expired") ||
                    normalized.contains("denied")
                )
        ) {
            return SteamAuthenticatorBindingFailureReason.OAUTH_INVALID
        }

        val mentionsWebApiKey = normalized.contains("web api key") ||
            normalized.contains("api key") ||
            normalized.contains("x-webapi-key") ||
            normalized.contains("publisher key")
        if (
            mentionsWebApiKey &&
            (
                normalized.contains("required") ||
                    normalized.contains("invalid") ||
                    normalized.contains("missing") ||
                    normalized.contains("denied") ||
                    normalized.contains("forbidden")
                )
        ) {
            return SteamAuthenticatorBindingFailureReason.WEB_API_KEY_INVALID
        }

        return SteamAuthenticatorBindingFailureReason.UNKNOWN
    }
}
