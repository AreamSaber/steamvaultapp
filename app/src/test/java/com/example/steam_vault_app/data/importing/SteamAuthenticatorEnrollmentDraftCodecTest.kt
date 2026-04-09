package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAuthenticatorEnrollmentDraftCodecTest {
    @Test
    fun encodeAndDecode_roundTripsDraft() {
        val draft = SteamAuthenticatorEnrollmentDraft(
            steamId = "76561198000000000",
            sessionId = "session-123",
            cookiesText = "sessionid=session-123; steamLoginSecure=token",
            currentUrl = "https://steamcommunity.com/login/home/",
            capturedAt = "2026-04-08T12:34:56Z",
            oauthToken = "oauth-token-123",
            webApiKey = "web-api-key-456",
        )

        val encoded = SteamAuthenticatorEnrollmentDraftCodec.encode(draft)
        val decoded = SteamAuthenticatorEnrollmentDraftCodec.decode(encoded)

        assertEquals(draft, decoded)
        assertEquals(draft.signature, decoded.signature)
    }

    @Test
    fun decode_rejectsUnsupportedVersion() {
        val rawDraft = """
            {
              "version": 2,
              "steam_id": "76561198000000000",
              "session_id": "session-123",
              "cookies_text": "sessionid=session-123",
              "current_url": "https://steamcommunity.com/login/home/",
              "captured_at": "2026-04-08T12:34:56Z",
              "oauth_token": "oauth-token-123",
              "web_api_key": "web-api-key-456"
            }
        """.trimIndent()

        val error = runCatching {
            SteamAuthenticatorEnrollmentDraftCodec.decode(rawDraft)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Unsupported Steam authenticator draft version") == true)
    }

    @Test
    fun decode_keepsWorkingWhenOauthTokenIsMissing() {
        val rawDraft = """
            {
              "version": 1,
              "steam_id": "76561198000000000",
              "session_id": "session-123",
              "cookies_text": "sessionid=session-123",
              "current_url": "https://steamcommunity.com/login/home/",
              "captured_at": "2026-04-08T12:34:56Z"
            }
        """.trimIndent()

        val decoded = SteamAuthenticatorEnrollmentDraftCodec.decode(rawDraft)

        assertEquals(null, decoded.oauthToken)
        assertEquals(null, decoded.webApiKey)
    }
}
