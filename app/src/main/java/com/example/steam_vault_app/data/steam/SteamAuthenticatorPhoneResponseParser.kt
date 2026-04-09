package com.example.steam_vault_app.data.steam

import org.json.JSONObject

internal object SteamAuthenticatorPhoneResponseParser {
    fun parseUserCountry(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ): String? {
        val payload = parsePayload(
            responseBody = responseBody,
            defaultFailureMessage = defaultFailureMessage,
            invalidResponseMessage = invalidResponseMessage,
        )
        return payload.optString("country").takeIf { it.isNotBlank() }
    }

    fun parsePhoneStatus(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ): SteamAuthenticatorPhoneStatusResult {
        val payload = parsePayload(
            responseBody = responseBody,
            defaultFailureMessage = defaultFailureMessage,
            invalidResponseMessage = invalidResponseMessage,
        )
        return SteamAuthenticatorPhoneStatusResult(
            verifiedPhone = payload.readBoolean("verified_phone"),
        )
    }

    fun parseSetPhoneNumber(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ): SteamAuthenticatorSetPhoneNumberResult {
        val payload = parsePayload(
            responseBody = responseBody,
            defaultFailureMessage = defaultFailureMessage,
            invalidResponseMessage = invalidResponseMessage,
        )
        return SteamAuthenticatorSetPhoneNumberResult(
            confirmationEmailAddress = payload.optString("confirmation_email_address")
                .takeIf { it.isNotBlank() },
            phoneNumberFormatted = payload.optString("phone_number_formatted")
                .takeIf { it.isNotBlank() },
        )
    }

    fun parseEmailConfirmationStatus(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ): SteamAuthenticatorEmailConfirmationStatus {
        val payload = parsePayload(
            responseBody = responseBody,
            defaultFailureMessage = defaultFailureMessage,
            invalidResponseMessage = invalidResponseMessage,
        )
        return SteamAuthenticatorEmailConfirmationStatus(
            awaitingEmailConfirmation = payload.readBoolean("awaiting_email_confirmation"),
            secondsToWait = payload.optInt("seconds_to_wait").takeIf { payload.has("seconds_to_wait") },
        )
    }

    fun parseVerifyPhone(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ) {
        parsePayload(
            responseBody = responseBody,
            defaultFailureMessage = defaultFailureMessage,
            invalidResponseMessage = invalidResponseMessage,
        )
    }

    private fun parsePayload(
        responseBody: String,
        defaultFailureMessage: String,
        invalidResponseMessage: String,
    ): JSONObject {
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
        val success = when {
            payload.has("success") -> payload.readBoolean("success", defaultValue = false)
            root.has("success") -> root.readBoolean("success", defaultValue = true)
            else -> true
        }
        if (!success) {
            val message = payload.optString("message").takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
            throw SteamAuthenticatorBindingException(
                reason = classifyFailure(message),
                message = message ?: defaultFailureMessage,
                rawResponse = responseBody,
            )
        }
        return payload
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

    private fun classifyFailure(message: String?): SteamAuthenticatorBindingFailureReason {
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

        return SteamAuthenticatorBindingFailureReason.UNKNOWN
    }
}
