package com.example.steam_vault_app.data.steam

enum class SteamAuthenticatorBindingFailureReason {
    SESSION_EXPIRED,
    PHONE_NUMBER_REQUIRED,
    AUTHENTICATOR_ALREADY_PRESENT,
    ACTIVATION_CODE_INVALID,
    RETRY_LATER,
    OAUTH_INVALID,
    WEB_API_KEY_INVALID,
    INVALID_RESPONSE,
    UNKNOWN,
}

class SteamAuthenticatorBindingException(
    val reason: SteamAuthenticatorBindingFailureReason,
    override val message: String,
    val httpStatusCode: Int? = null,
    val steamStatusCode: Int? = null,
    val rawResponse: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
