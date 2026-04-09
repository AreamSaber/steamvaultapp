package com.example.steam_vault_app.data.steam

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object SteamQrLoginChallengeParser {
    fun parseClientId(challengeUrl: String): ULong {
        return parseClientIdOrNull(challengeUrl)
            ?: throw IllegalArgumentException(
                "Steam QR challenge URL does not contain a valid client_id.",
            )
    }

    suspend fun resolveClientId(
        challengeUrl: String,
        client: OkHttpClient = redirectFollowingClient,
    ): ULong {
        parseClientIdOrNull(challengeUrl)?.let { return it }

        val normalizedChallengeUrl = normalizeChallengeUrl(challengeUrl)
        val resolvedPayload = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(normalizedChallengeUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val responseBody = response.body?.string().orEmpty()
                finalUrl + "\n" + responseBody
            }
        }

        return parseClientIdOrNull(resolvedPayload)
            ?: throw IllegalArgumentException(
                "Steam QR challenge URL does not contain a valid client_id.",
            )
    }

    private fun parseClientIdOrNull(challengeUrl: String): ULong? {
        val normalizedChallengeUrl = normalizeChallengeUrl(challengeUrl)
        val outerUrl = normalizedChallengeUrl.toHttpUrlOrNull()
        if (outerUrl != null) {
            val shortUrlPathClientId = outerUrl.takeIf {
                it.host.equals("s.team", ignoreCase = true) &&
                    it.pathSegments.firstOrNull().equals("q", ignoreCase = true)
            }?.pathSegments
                ?.lastOrNull()
                ?.toULongOrNull()
            val directClientId = outerUrl.queryParameter("client_id")
            val nestedClientId = outerUrl.queryParameter("q")
                ?.let(::decodeQueryValue)
                ?.let(::resolveNestedUrl)
                ?.queryParameter("client_id")
            return shortUrlPathClientId ?: (nestedClientId ?: directClientId)?.toULongOrNull()
        }

        return clientIdPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalizedChallengeUrl)?.groupValues?.getOrNull(1)?.toULongOrNull()
        }
    }

    private fun normalizeChallengeUrl(challengeUrl: String): String {
        val normalizedChallengeUrl = challengeUrl.trim()
        require(normalizedChallengeUrl.isNotEmpty()) { "Steam QR challenge URL is empty." }

        return when {
            "://" in normalizedChallengeUrl -> normalizedChallengeUrl
            normalizedChallengeUrl.startsWith("s.team/", ignoreCase = true) ->
                "https://$normalizedChallengeUrl"
            normalizedChallengeUrl.startsWith("www.s.team/", ignoreCase = true) ->
                "https://$normalizedChallengeUrl"
            else -> normalizedChallengeUrl
        }
    }

    private fun decodeQueryValue(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun resolveNestedUrl(value: String) = runCatching {
        URI("https://dummy.local").resolve(value).toString().toHttpUrlOrNull()
    }.getOrNull()

    private val clientIdPatterns = listOf(
        Regex("""(?:^|[?&])client_id=(\d+)"""),
        Regex("""client_id%3[Dd](\d+)"""),
        Regex(""""client_id"\s*:\s*"(\d+)""""),
    )

    private val redirectFollowingClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
