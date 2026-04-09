package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpSteamTimeApiClient(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient(),
) : SteamTimeApiClient {
    private val messages = Messages.fromContext(context)

    override suspend fun queryServerTimeSeconds(): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(QUERY_TIME_URL)
            .post(
                FormBody.Builder()
                    .add("steamid", "0")
                    .build(),
            )
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    buildString {
                        append(messages.queryFailed)
                        append(" ")
                        append(
                            if (responseBody.isBlank()) {
                                "(HTTP ${response.code})"
                            } else {
                                "(HTTP ${response.code}: ${responseBody.take(MAX_ERROR_BODY_LENGTH)})"
                            },
                        )
                    },
                )
            }

            try {
                SteamTimeResponseParser.parseServerTimeSeconds(responseBody)
            } catch (_: IllegalArgumentException) {
                throw IllegalStateException(messages.invalidResponse)
            }
        }
    }

    private data class Messages(
        val queryFailed: String,
        val invalidResponse: String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        queryFailed = "Unable to query Steam server time.",
                        invalidResponse = "Steam time response was invalid.",
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    queryFailed = appContext.getString(R.string.steam_time_query_failed),
                    invalidResponse = appContext.getString(R.string.steam_time_response_invalid),
                )
            }
        }
    }

    private companion object {
        const val QUERY_TIME_URL = "https://api.steampowered.com/ITwoFactorService/QueryTime/v0001"
        const val MAX_ERROR_BODY_LENGTH = 160
    }
}
