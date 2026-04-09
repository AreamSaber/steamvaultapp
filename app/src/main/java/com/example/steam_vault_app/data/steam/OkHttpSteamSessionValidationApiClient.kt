package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OkHttpSteamSessionValidationApiClient(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient(),
    private val validationUrl: String = DEFAULT_VALIDATION_URL,
) : SteamSessionValidationApiClient {
    private val messages = Messages.fromContext(context)

    override suspend fun validateSession(
        request: SteamSessionValidationRequest,
    ): SteamSessionValidationResult = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url(validationUrl)
            .header("Cookie", buildCookieHeader(request.cookies))
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val finalUrl = response.request.url.toString()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    buildString {
                        append(messages.validationFailed)
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
                )
            }

            if (looksLikeSessionExpired(finalUrl, responseBody)) {
                throw IllegalStateException(messages.sessionExpired)
            }

            SteamSessionValidationResult(
                resolvedSteamId = extractSteamId(finalUrl, responseBody) ?: request.steamIdHint,
                profileUrl = finalUrl,
            )
        }
    }

    private fun buildCookieHeader(cookies: List<com.example.steam_vault_app.domain.model.SteamSessionCookie>): String {
        return cookies.joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    }

    private fun looksLikeSessionExpired(
        finalUrl: String,
        responseBody: String,
    ): Boolean {
        return finalUrl.contains("/login/", ignoreCase = true) ||
            responseBody.contains("g_steamID = false", ignoreCase = true) ||
            responseBody.contains("loginForm", ignoreCase = true)
    }

    private fun extractSteamId(
        finalUrl: String,
        responseBody: String,
    ): String? {
        PROFILE_URL_REGEX.find(finalUrl)?.groupValues?.getOrNull(1)?.let { return it }
        HTML_STEAM_ID_REGEX.find(responseBody)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private data class Messages(
        val validationFailed: String,
        val sessionExpired: String,
        val httpSuffixWithoutBody: (Int) -> String,
        val httpSuffixWithBody: (Int, String) -> String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        validationFailed = "Unable to validate Steam session.",
                        sessionExpired = "Steam session has expired. Please log in again.",
                        httpSuffixWithoutBody = { code -> "(HTTP $code)" },
                        httpSuffixWithBody = { code, body -> "(HTTP $code: $body)" },
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    validationFailed = appContext.getString(R.string.steam_session_validation_failed),
                    sessionExpired = appContext.getString(R.string.steam_session_validation_session_expired),
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
        private const val DEFAULT_VALIDATION_URL = "https://steamcommunity.com/my/"
        private const val MAX_ERROR_BODY_LENGTH = 160
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Steam Vault) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
        private val PROFILE_URL_REGEX = Regex("/profiles/(\\d+)", RegexOption.IGNORE_CASE)
        private val HTML_STEAM_ID_REGEX = Regex("g_steamID\\s*=\\s*\"(\\d+)\"", RegexOption.IGNORE_CASE)
    }
}
