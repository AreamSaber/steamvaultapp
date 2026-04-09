package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamSessionCookie

data class SteamAuthenticatorBeginRequest(
    val steamId: String,
    val oauthToken: String? = null,
    val webApiKey: String? = null,
    val deviceId: String,
    val sessionId: String,
    val cookies: List<SteamSessionCookie>,
)

data class SteamAuthenticatorBeginResult(
    val steamId: String,
    val accountName: String? = null,
    val sharedSecret: String,
    val identitySecret: String? = null,
    val serialNumber: String? = null,
    val revocationCode: String? = null,
    val secret1: String? = null,
    val tokenGid: String? = null,
    val uri: String? = null,
    val serverTimeSeconds: Long? = null,
    val status: Int? = null,
    val fullyEnrolled: Boolean = false,
    val rawResponse: String? = null,
)

data class SteamAuthenticatorFinalizeRequest(
    val steamId: String,
    val oauthToken: String? = null,
    val webApiKey: String? = null,
    val deviceId: String,
    val activationCode: String,
    val authenticatorCode: String,
    val authenticatorTimeSeconds: Long,
    val sessionId: String,
    val cookies: List<SteamSessionCookie>,
)

data class SteamAuthenticatorFinalizeResult(
    val success: Boolean,
    val status: Int? = null,
    val serverTimeSeconds: Long? = null,
    val wantsMoreActivation: Boolean = false,
    val rawResponse: String? = null,
)

data class SteamAuthenticatorPhoneAccessRequest(
    val oauthToken: String,
    val steamId: String? = null,
)

data class SteamAuthenticatorPhoneStatusResult(
    val verifiedPhone: Boolean,
)

data class SteamAuthenticatorSetPhoneNumberRequest(
    val oauthToken: String,
    val phoneNumber: String,
    val countryCode: String,
)

data class SteamAuthenticatorSetPhoneNumberResult(
    val confirmationEmailAddress: String? = null,
    val phoneNumberFormatted: String? = null,
)

data class SteamAuthenticatorEmailConfirmationStatus(
    val awaitingEmailConfirmation: Boolean,
    val secondsToWait: Int? = null,
)

data class SteamAuthenticatorVerifyPhoneCodeRequest(
    val oauthToken: String,
    val code: String,
)

interface SteamAuthenticatorBindingApiClient {
    suspend fun beginAuthenticatorBinding(
        request: SteamAuthenticatorBeginRequest,
    ): SteamAuthenticatorBeginResult

    suspend fun finalizeAuthenticatorBinding(
        request: SteamAuthenticatorFinalizeRequest,
    ): SteamAuthenticatorFinalizeResult

    suspend fun getUserCountry(
        request: SteamAuthenticatorPhoneAccessRequest,
    ): String?

    suspend fun getPhoneStatus(
        request: SteamAuthenticatorPhoneAccessRequest,
    ): SteamAuthenticatorPhoneStatusResult

    suspend fun setPhoneNumber(
        request: SteamAuthenticatorSetPhoneNumberRequest,
    ): SteamAuthenticatorSetPhoneNumberResult

    suspend fun getEmailConfirmationStatus(
        request: SteamAuthenticatorPhoneAccessRequest,
    ): SteamAuthenticatorEmailConfirmationStatus

    suspend fun sendPhoneVerificationCode(
        request: SteamAuthenticatorPhoneAccessRequest,
    )

    suspend fun verifyPhoneWithCode(
        request: SteamAuthenticatorVerifyPhoneCodeRequest,
    )
}
