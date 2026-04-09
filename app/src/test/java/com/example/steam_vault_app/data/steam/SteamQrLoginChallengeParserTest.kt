package com.example.steam_vault_app.data.steam

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamQrLoginChallengeParserTest {
    @Test
    fun parseClientId_supportsRawSteamShortUrls() {
        val clientId = SteamQrLoginChallengeParser.parseClientId(
            "https://s.team/q/1/7070887999014180207",
        )

        assertEquals(7070887999014180207uL, clientId)
    }

    @Test
    fun parseClientId_supportsUnsignedClientIdsAboveLongMaxValue() {
        val clientId = SteamQrLoginChallengeParser.parseClientId(
            "https://s.team/q/1/17658147338733475644",
        )

        assertEquals(17658147338733475644uL, clientId)
    }

    @Test
    fun parseClientId_supportsDirectChallengeUrls() {
        val clientId = SteamQrLoginChallengeParser.parseClientId(
            "https://s.team/q/1/abc?client_id=123456789",
        )

        assertEquals(123456789uL, clientId)
    }

    @Test
    fun parseClientId_supportsNestedQQueryParameter() {
        val clientId = SteamQrLoginChallengeParser.parseClientId(
            "https://s.team/q/1/abc?q=%2Flogin%2Fhome%2F%3Fclient_id%3D76561198000000000",
        )

        assertEquals(76561198000000000uL, clientId)
    }

    @Test
    fun resolveClientId_supportsShortUrlRedirects() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader(
                        "Location",
                        "/login/home/?client_id=123456789",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("ok"),
            )

            val clientId = SteamQrLoginChallengeParser.resolveClientId(
                challengeUrl = server.url("/q/1/abc").toString(),
                client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build(),
            )

            assertEquals(123456789uL, clientId)
        }
    }
}
