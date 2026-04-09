package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamSessionCookie
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpSteamConfirmationApiClientTest {
    @Test
    fun fetchConfirmations_usesReactPlatformMarker() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                            {
                              "success": true,
                              "conf": [
                                {
                                  "id": "1001",
                                  "nonce": "nonce-1001",
                                  "headline": "Trade Offer"
                                }
                              ]
                            }
                        """.trimIndent(),
                    ),
            )

            val client = OkHttpSteamConfirmationApiClient(
                client = OkHttpClient(),
                baseUrl = server.url("/").toString(),
            )

            val confirmations = client.fetchConfirmations(demoRequest(tag = "conf"))

            assertEquals(1, confirmations.size)
            val recordedRequest = server.takeRequest()
            assertEquals("/mobileconf/getlist", recordedRequest.requestUrl?.encodedPath)
            assertEquals("react", recordedRequest.requestUrl?.queryParameter("m"))
            assertEquals("conf", recordedRequest.requestUrl?.queryParameter("tag"))
        }
    }

    @Test
    fun resolveConfirmation_rejectUsesCancelOperationAndRejectTag() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"success":true}"""),
            )

            val client = OkHttpSteamConfirmationApiClient(
                client = OkHttpClient(),
                baseUrl = server.url("/").toString(),
            )

            client.resolveConfirmation(
                request = demoRequest(tag = "reject"),
                confirmationId = "1002",
                confirmationNonce = "nonce-1002",
                approve = false,
            )

            val recordedRequest = server.takeRequest()
            assertEquals("/mobileconf/ajaxop", recordedRequest.requestUrl?.encodedPath)
            assertEquals("cancel", recordedRequest.requestUrl?.queryParameter("op"))
            assertEquals("reject", recordedRequest.requestUrl?.queryParameter("tag"))
            assertEquals("1002", recordedRequest.requestUrl?.queryParameter("cid"))
            assertEquals("nonce-1002", recordedRequest.requestUrl?.queryParameter("ck"))
            assertTrue(recordedRequest.getHeader("Cookie")?.contains("steamLoginSecure=secure") == true)
        }
    }

    private fun demoRequest(tag: String) = SteamConfirmationRequest(
        steamId = "76561198000000001",
        deviceId = "android:demo-device",
        cookies = listOf(
            SteamSessionCookie(name = "steamLoginSecure", value = "secure"),
            SteamSessionCookie(name = "sessionid", value = "session-cookie"),
        ),
        timestampSeconds = 1_700_000_000L,
        confirmationKey = "demo-key",
        tag = tag,
    )
}
