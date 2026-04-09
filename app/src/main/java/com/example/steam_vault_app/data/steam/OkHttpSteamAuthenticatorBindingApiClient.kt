package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpSteamAuthenticatorBindingApiClient(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient(),
    private val beginUrl: String = DEFAULT_BEGIN_URL,
    private val finalizeUrl: String = DEFAULT_FINALIZE_URL,
) : SteamAuthenticatorBindingApiClient {
    private val messages = Messages.fromContext(context)

    override suspend fun beginAuthenticatorBinding(
        request: SteamAuthenticatorBeginRequest,
    ): SteamAuthenticatorBeginResult = withContext(Dispatchers.IO) {
        val bodyBuilder = FormBody.Builder()
            .add("steamid", request.steamId)
            .add("authenticator_type", AUTHENTICATOR_TYPE_MOBILE)
            .add("device_identifier", request.deviceId)
            .add("sms_phone_id", DEFAULT_PHONE_ID)
        request.oauthToken
            ?.takeIf { it.isNotBlank() }
            ?.let { bodyBuilder.add("access_token", it) }
        val responseBody = executeRequest(
            url = beginUrl,
            formBody = bodyBuilder.build(),
            sessionId = request.sessionId,
            cookies = request.cookies,
            webApiKey = request.webApiKey,
            defaultFailureMessage = messages.beginFailed,
        )
        SteamAuthenticatorBindingResponseParser.parseBeginResult(
            responseBody = responseBody,
            steamId = request.steamId,
            defaultFailureMessage = messages.beginFailed,
            invalidResponseMessage = messages.invalidResponse,
            phoneNumberRequiredMessage = messages.phoneNumberRequired,
            authenticatorPresentMessage = messages.authenticatorPresent,
        )
    }

    override suspend fun finalizeAuthenticatorBinding(
        request: SteamAuthenticatorFinalizeRequest,
    ): SteamAuthenticatorFinalizeResult = withContext(Dispatchers.IO) {
        val bodyBuilder = FormBody.Builder()
            .add("steamid", request.steamId)
            .add("activation_code", request.activationCode)
            .add("authenticator_code", request.authenticatorCode)
            .add("authenticator_time", request.authenticatorTimeSeconds.toString())
            .add("device_identifier", request.deviceId)
        request.oauthToken
            ?.takeIf { it.isNotBlank() }
            ?.let { bodyBuilder.add("access_token", it) }
        val responseBody = executeRequest(
            url = finalizeUrl,
            formBody = bodyBuilder.build(),
            sessionId = request.sessionId,
            cookies = request.cookies,
            webApiKey = request.webApiKey,
            defaultFailureMessage = messages.finalizeFailed,
        )
        SteamAuthenticatorBindingResponseParser.parseFinalizeResult(
            responseBody = responseBody,
            defaultFailureMessage = messages.finalizeFailed,
            invalidResponseMessage = messages.invalidResponse,
        )
    }

    override suspend fun getUserCountry(
        request: SteamAuthenticatorPhoneAccessRequest,
    ): String? = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder().apply {
            request.steamId?.takeIf { it.isNotBlank() }?.let { add("steamid", it) }
        }.build()
        val responseBody = executeAccessTokenRequest(
            url = userCountryUrl,
            oauthToken = request.oauthToken,
            formBody = formBody,
            defaultFailureMessage = messages.phoneCountryFailed,
        )
        SteamAuthenticatorPhoneResponseParser.parseUserCountry(
            responseBody = responseBody,
            defaultFailureMessage = messages.phoneCountryFailed,
            invalidResponseMessage = messages.invalidResponse,
        )
    }

    override suspend fun getPhoneStatus(
        request: SteamAuthenticatorPhoneAccessRequest,
    ): SteamAuthenticatorPhoneStatusResult = withContext(Dispatchers.IO) {
        val responseBody = executeAccessTokenRequest(
            url = phoneStatusUrl,
            oauthToken = request.oauthToken,
            formBody = FormBody.Builder().build(),
            defaultFailureMessage = messages.phoneStatusFailed,
        )
        SteamAuthenticatorPhoneResponseParser.parsePhoneStatus(
            responseBody = responseBody,
            defaultFailureMessage = messages.phoneStatusFailed,
            invalidResponseMessage = messages.invalidResponse,
        )
    }

    override suspend fun setPhoneNumber(
        request: SteamAuthenticatorSetPhoneNumberRequest,
    ): SteamAuthenticatorSetPhoneNumberResult = withContext(Dispatchers.IO) {
        val responseBody = executeAccessTokenRequest(
            url = setPhoneNumberUrl,
            oauthToken = request.oauthToken,
            formBody = FormBody.Builder()
                .add("phone_number", request.phoneNumber)
                .add("phone_country_code", request.countryCode)
                .build(),
            defaultFailureMessage = messages.phoneSetFailed,
        )
        SteamAuthenticatorPhoneResponseParser.parseSetPhoneNumber(
            responseBody = responseBody,
            defaultFailureMessage = messages.phoneSetFailed,
            invalidResponseMessage = messages.invalidResponse,
        )
    }

    override suspend fun getEmailConfirmationStatus(
        request: SteamAuthenticatorPhoneAccessRequest,
    ): SteamAuthenticatorEmailConfirmationStatus = withContext(Dispatchers.IO) {
        val responseBody = executeAccessTokenRequest(
            url = emailConfirmationStatusUrl,
            oauthToken = request.oauthToken,
            formBody = FormBody.Builder().build(),
            defaultFailureMessage = messages.phoneEmailStatusFailed,
        )
        SteamAuthenticatorPhoneResponseParser.parseEmailConfirmationStatus(
            responseBody = responseBody,
            defaultFailureMessage = messages.phoneEmailStatusFailed,
            invalidResponseMessage = messages.invalidResponse,
        )
    }

    override suspend fun sendPhoneVerificationCode(
        request: SteamAuthenticatorPhoneAccessRequest,
    ) = withContext(Dispatchers.IO) {
        executeAccessTokenRequest(
            url = sendPhoneVerificationCodeUrl,
            oauthToken = request.oauthToken,
            formBody = FormBody.Builder().build(),
            defaultFailureMessage = messages.phoneSendSmsFailed,
        )
        Unit
    }

    override suspend fun verifyPhoneWithCode(
        request: SteamAuthenticatorVerifyPhoneCodeRequest,
    ) = withContext(Dispatchers.IO) {
        val responseBody = executeAccessTokenRequest(
            url = verifyPhoneCodeUrl,
            oauthToken = request.oauthToken,
            formBody = FormBody.Builder()
                .add("code", request.code)
                .build(),
            defaultFailureMessage = messages.phoneVerifyFailed,
        )
        SteamAuthenticatorPhoneResponseParser.parseVerifyPhone(
            responseBody = responseBody,
            defaultFailureMessage = messages.phoneVerifyFailed,
            invalidResponseMessage = messages.invalidResponse,
        )
        Unit
    }

    private fun executeRequest(
        url: String,
        formBody: FormBody,
        sessionId: String,
        cookies: List<com.example.steam_vault_app.domain.model.SteamSessionCookie>,
        webApiKey: String?,
        defaultFailureMessage: String,
    ): String {
        val requestUrl = url.toHttpUrl().newBuilder().apply {
            addQueryParameter("format", "json")
            webApiKey?.takeIf { it.isNotBlank() }?.let { addQueryParameter("key", it) }
        }.build()
        val request = Request.Builder()
            .url(requestUrl)
            .post(formBody)
            .header("Cookie", buildCookieHeader(sessionId, cookies))
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", STEAM_COMMUNITY_ORIGIN)
            .header("Referer", STEAM_COMMUNITY_REFERER)
            .header("User-Agent", USER_AGENT)
            .apply {
                webApiKey?.takeIf { it.isNotBlank() }?.let { header("x-webapi-key", it) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SteamAuthenticatorBindingException(
                    reason = classifyHttpFailureReason(
                        httpStatusCode = response.code,
                        responseBody = responseBody,
                    ),
                    message = buildString {
                        append(defaultFailureMessage)
                        append(" ")
                        append(
                            if (responseBody.isBlank()) {
                                messages.httpSuffixWithoutBody(response.code)
                            } else {
                                messages.httpSuffixWithBody(
                                    response.code,
                                    responseBody.take(MAX_ERROR_BODY_LENGTH),
                                )
                            },
                        )
                    },
                    httpStatusCode = response.code,
                    rawResponse = responseBody,
                )
            }

            if (looksLikeSessionExpired(responseBody)) {
                throw SteamAuthenticatorBindingException(
                    reason = SteamAuthenticatorBindingFailureReason.SESSION_EXPIRED,
                    message = messages.sessionExpired,
                    rawResponse = responseBody,
                )
            }

            return responseBody
        }
    }

    private fun executeAccessTokenRequest(
        url: String,
        oauthToken: String,
        formBody: FormBody,
        defaultFailureMessage: String,
    ): String {
        val requestUrl = url.toHttpUrl().newBuilder().apply {
            addQueryParameter("access_token", oauthToken)
            addQueryParameter("format", "json")
        }.build()
        val request = Request.Builder()
            .url(requestUrl)
            .post(formBody)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", STEAM_COMMUNITY_ORIGIN)
            .header("Referer", STEAM_COMMUNITY_REFERER)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SteamAuthenticatorBindingException(
                    reason = classifyHttpFailureReason(
                        httpStatusCode = response.code,
                        responseBody = responseBody,
                    ),
                    message = buildString {
                        append(defaultFailureMessage)
                        append(" ")
                        append(
                            if (responseBody.isBlank()) {
                                messages.httpSuffixWithoutBody(response.code)
                            } else {
                                messages.httpSuffixWithBody(
                                    response.code,
                                    responseBody.take(MAX_ERROR_BODY_LENGTH),
                                )
                            },
                        )
                    },
                    httpStatusCode = response.code,
                    rawResponse = responseBody,
                )
            }

            if (looksLikeSessionExpired(responseBody)) {
                throw SteamAuthenticatorBindingException(
                    reason = SteamAuthenticatorBindingFailureReason.SESSION_EXPIRED,
                    message = messages.sessionExpired,
                    rawResponse = responseBody,
                )
            }

            return responseBody
        }
    }

    private fun buildCookieHeader(
        sessionId: String,
        cookies: List<com.example.steam_vault_app.domain.model.SteamSessionCookie>,
    ): String {
        val normalizedCookies = cookies
            .filterNot { it.name.equals("sessionid", ignoreCase = true) } +
            com.example.steam_vault_app.domain.model.SteamSessionCookie(
                name = "sessionid",
                value = sessionId,
            )

        return normalizedCookies.joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    }

    private fun looksLikeSessionExpired(responseBody: String): Boolean {
        val trimmed = responseBody.trimStart()
        if (trimmed.startsWith("<")) {
            return true
        }
        return trimmed.contains("loginForm", ignoreCase = true) ||
            trimmed.contains("Access Denied", ignoreCase = true)
    }

    private fun classifyHttpFailureReason(
        httpStatusCode: Int,
        responseBody: String,
    ): SteamAuthenticatorBindingFailureReason {
        if (httpStatusCode == 401 || httpStatusCode == 403) {
            return SteamAuthenticatorBindingFailureReason.SESSION_EXPIRED
        }
        if (httpStatusCode == 429) {
            return SteamAuthenticatorBindingFailureReason.RETRY_LATER
        }

        val normalized = responseBody.lowercase()
        return when {
            normalized.contains("rate limit") ||
                normalized.contains("rate-limited") ||
                normalized.contains("try again later") ||
                normalized.contains("too many") -> {
                SteamAuthenticatorBindingFailureReason.RETRY_LATER
            }

            normalized.contains("access token") ||
                normalized.contains("oauth") -> {
                SteamAuthenticatorBindingFailureReason.OAUTH_INVALID
            }

            normalized.contains("web api key") ||
                normalized.contains("api key") ||
                normalized.contains("x-webapi-key") ||
                normalized.contains("publisher key") -> {
                SteamAuthenticatorBindingFailureReason.WEB_API_KEY_INVALID
            }

            else -> SteamAuthenticatorBindingFailureReason.UNKNOWN
        }
    }

    private data class Messages(
        val beginFailed: String,
        val finalizeFailed: String,
        val invalidResponse: String,
        val phoneNumberRequired: String,
        val authenticatorPresent: String,
        val phoneCountryFailed: String,
        val phoneStatusFailed: String,
        val phoneSetFailed: String,
        val phoneEmailStatusFailed: String,
        val phoneSendSmsFailed: String,
        val phoneVerifyFailed: String,
        val sessionExpired: String,
        val httpSuffixWithoutBody: (Int) -> String,
        val httpSuffixWithBody: (Int, String) -> String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        beginFailed = "Unable to start Steam authenticator binding.",
                        finalizeFailed = "Unable to finalize Steam authenticator binding.",
                        invalidResponse = "Steam returned an unexpected authenticator binding response.",
                        phoneNumberRequired = "Steam requires a verified phone number before adding a mobile authenticator.",
                        authenticatorPresent = "Steam reports that a mobile authenticator is already present on this account.",
                        phoneCountryFailed = "Unable to determine the Steam account country for phone setup.",
                        phoneStatusFailed = "Unable to check whether the Steam account already has a verified phone number.",
                        phoneSetFailed = "Unable to add a phone number to the Steam account.",
                        phoneEmailStatusFailed = "Unable to check whether the Steam confirmation email has been approved.",
                        phoneSendSmsFailed = "Unable to ask Steam to send an SMS verification code.",
                        phoneVerifyFailed = "Unable to verify the Steam phone number with the provided SMS code.",
                        sessionExpired = "Steam session has expired. Please log in again.",
                        httpSuffixWithoutBody = { code -> "(HTTP $code)" },
                        httpSuffixWithBody = { code, body -> "(HTTP $code: $body)" },
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    beginFailed = appContext.getString(
                        R.string.steam_authenticator_binding_begin_failed_generic,
                    ),
                    finalizeFailed = appContext.getString(
                        R.string.steam_authenticator_binding_finalize_failed_generic,
                    ),
                    invalidResponse = appContext.getString(
                        R.string.steam_authenticator_binding_invalid_response,
                    ),
                    phoneNumberRequired = appContext.getString(
                        R.string.steam_authenticator_binding_begin_phone_required,
                    ),
                    authenticatorPresent = appContext.getString(
                        R.string.steam_authenticator_binding_begin_authenticator_present,
                    ),
                    phoneCountryFailed = appContext.getString(
                        R.string.steam_authenticator_binding_phone_country_failed,
                    ),
                    phoneStatusFailed = appContext.getString(
                        R.string.steam_authenticator_binding_phone_status_failed,
                    ),
                    phoneSetFailed = appContext.getString(
                        R.string.steam_authenticator_binding_phone_set_failed,
                    ),
                    phoneEmailStatusFailed = appContext.getString(
                        R.string.steam_authenticator_binding_phone_email_status_failed,
                    ),
                    phoneSendSmsFailed = appContext.getString(
                        R.string.steam_authenticator_binding_phone_send_sms_failed,
                    ),
                    phoneVerifyFailed = appContext.getString(
                        R.string.steam_authenticator_binding_phone_verify_failed,
                    ),
                    sessionExpired = appContext.getString(
                        R.string.steam_authenticator_binding_session_expired,
                    ),
                    httpSuffixWithoutBody = { code ->
                        appContext.getString(R.string.webdav_http_suffix_without_body, code)
                    },
                    httpSuffixWithBody = { code, body ->
                        appContext.getString(R.string.webdav_http_suffix_with_body, code, body)
                    },
                )
            }
        }
    }

    private companion object {
        private const val DEFAULT_BEGIN_URL =
            "https://api.steampowered.com/ITwoFactorService/AddAuthenticator/v0001"
        private const val DEFAULT_FINALIZE_URL =
            "https://api.steampowered.com/ITwoFactorService/FinalizeAddAuthenticator/v0001"
        private const val DEFAULT_USER_COUNTRY_URL =
            "https://api.steampowered.com/IUserAccountService/GetUserCountry/v1"
        private const val DEFAULT_PHONE_STATUS_URL =
            "https://api.steampowered.com/IPhoneService/AccountPhoneStatus/v1"
        private const val DEFAULT_SET_PHONE_NUMBER_URL =
            "https://api.steampowered.com/IPhoneService/SetAccountPhoneNumber/v1"
        private const val DEFAULT_EMAIL_CONFIRMATION_STATUS_URL =
            "https://api.steampowered.com/IPhoneService/IsAccountWaitingForEmailConfirmation/v1"
        private const val DEFAULT_SEND_PHONE_VERIFICATION_CODE_URL =
            "https://api.steampowered.com/IPhoneService/SendPhoneVerificationCode/v1"
        private const val DEFAULT_VERIFY_PHONE_CODE_URL =
            "https://api.steampowered.com/IPhoneService/VerifyAccountPhoneWithCode/v1"
        private const val AUTHENTICATOR_TYPE_MOBILE = "1"
        private const val DEFAULT_PHONE_ID = "1"
        private const val MAX_ERROR_BODY_LENGTH = 160
        private const val STEAM_COMMUNITY_ORIGIN = "https://steamcommunity.com"
        private const val STEAM_COMMUNITY_REFERER = "https://steamcommunity.com/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Steam Vault) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
    }

    private val userCountryUrl: String
        get() = DEFAULT_USER_COUNTRY_URL

    private val phoneStatusUrl: String
        get() = DEFAULT_PHONE_STATUS_URL

    private val setPhoneNumberUrl: String
        get() = DEFAULT_SET_PHONE_NUMBER_URL

    private val emailConfirmationStatusUrl: String
        get() = DEFAULT_EMAIL_CONFIRMATION_STATUS_URL

    private val sendPhoneVerificationCodeUrl: String
        get() = DEFAULT_SEND_PHONE_VERIFICATION_CODE_URL

    private val verifyPhoneCodeUrl: String
        get() = DEFAULT_VERIFY_PHONE_CODE_URL
}
