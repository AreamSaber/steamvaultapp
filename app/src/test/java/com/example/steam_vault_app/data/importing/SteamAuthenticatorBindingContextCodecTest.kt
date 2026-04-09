package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamAuthenticatorBindingContextCodecTest {
    @Test
    fun encodeThenDecode_roundTripsStructuredProtocolSession() {
        val context = SteamAuthenticatorBindingContext(
            accountName = "demo-account",
            session = SteamMobileSession(
                steamId = "76561198000000000",
                accessToken = "access-token-123",
                refreshToken = "refresh-token-456",
                guardData = "guard-data-789",
                sessionId = "session-id-123",
                oauthToken = "oauth-token-123",
                cookies = listOf(
                    SteamSessionCookie(name = "sessionid", value = "session-id-123"),
                    SteamSessionCookie(name = "steamLoginSecure", value = "76561198000000000||token"),
                ),
            ),
            capturedAt = "2026-04-09T08:00:00Z",
        )

        val decoded = SteamAuthenticatorBindingContextCodec.decode(
            SteamAuthenticatorBindingContextCodec.encode(context),
        )

        assertEquals(context.accountName, decoded.accountName)
        assertEquals(context.session, decoded.session)
        assertEquals(context.capturedAt, decoded.capturedAt)
        assertNull(decoded.webApiKey)
    }
}
