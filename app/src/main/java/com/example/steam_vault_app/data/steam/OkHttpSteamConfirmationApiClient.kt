package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OkHttpSteamConfirmationApiClient(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SteamConfirmationApiClient {
    private val messages = Messages.fromContext(context)

    override suspend fun fetchConfirmations(
        request: SteamConfirmationRequest,
    ) = withContext(Dispatchers.IO) {
        val url = buildBaseQuery(
            path = "mobileconf/getlist",
            request = request,
        ).build()
        val responseBody = executeRequest(
            url = url.toString(),
            request = request,
            defaultFailureMessage = messages.listFailed,
        )
        SteamConfirmationResponseParser.parseConfirmationList(
            responseBody = responseBody,
            defaultFailureMessage = messages.listFailed,
        )
    }

    override suspend fun resolveConfirmation(
        request: SteamConfirmationRequest,
        confirmationId: String,
        confirmationNonce: String,
        approve: Boolean,
    ) = withContext(Dispatchers.IO) {
        val operation = if (approve) "allow" else "cancel"
        val url = buildBaseQuery(
            path = "mobileconf/ajaxop",
            request = request,
        )
            .addQueryParameter("op", operation)
            .addQueryParameter("cid", confirmationId)
            .addQueryParameter("ck", confirmationNonce)
            .build()
        val responseBody = executeRequest(
            url = url.toString(),
            request = request,
            defaultFailureMessage = messages.actionFailed,
        )
        SteamConfirmationResponseParser.ensureActionSucceeded(
            responseBody = responseBody,
            defaultFailureMessage = messages.actionFailed,
        )
    }

    private fun buildBaseQuery(
        path: String,
        request: SteamConfirmationRequest,
    ) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .addQueryParameter("p", request.deviceId)
        .addQueryParameter("a", request.steamId)
        .addQueryParameter("k", request.confirmationKey)
        .addQueryParameter("t", request.timestampSeconds.toString())
        .addQueryParameter("m", PLATFORM_REACT)
        .addQueryParameter("tag", request.tag)

    private fun executeRequest(
        url: String,
        request: SteamConfirmationRequest,
        defaultFailureMessage: String,
    ): String {
        val httpRequest = Request.Builder()
            .url(url)
            .header("Cookie", buildCookieHeader(request))
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    buildString {
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
                )
            }

            if (looksLikeSessionExpired(responseBody)) {
                throw IllegalStateException(messages.sessionExpired)
            }

            return responseBody
        }
    }

    private fun buildCookieHeader(request: SteamConfirmationRequest): String {
        return request.cookies.joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    }

    private fun looksLikeSessionExpired(responseBody: String): Boolean {
        val trimmed = responseBody.trimStart()
        if (trimmed.startsWith("<")) {
            return true
        }
        return trimmed.contains("g_steamID = false", ignoreCase = true) ||
            trimmed.contains("loginForm", ignoreCase = true)
    }

    private data class Messages(
        val listFailed: String,
        val actionFailed: String,
        val sessionExpired: String,
        val httpSuffixWithoutBody: (Int) -> String,
        val httpSuffixWithBody: (Int, String) -> String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        listFailed = "Unable to fetch Steam confirmations.",
                        actionFailed = "Unable to submit Steam confirmation action.",
                        sessionExpired = "Steam session has expired. Please log in again.",
                        httpSuffixWithoutBody = { code -> "(HTTP $code)" },
                        httpSuffixWithBody = { code, body -> "(HTTP $code: $body)" },
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    listFailed = appContext.getString(R.string.steam_confirmation_list_failed_generic),
                    actionFailed = appContext.getString(R.string.steam_confirmation_action_failed_generic),
                    sessionExpired = appContext.getString(R.string.steam_confirmation_session_expired),
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
        private const val DEFAULT_BASE_URL = "https://steamcommunity.com/"
        private const val PLATFORM_REACT = "react"
        private const val MAX_ERROR_BODY_LENGTH = 160
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Steam Vault) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
    }
}
