package com.example.steam_vault_app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamMobileSessionTest {
    @Test
    fun toRecord_andFromRecord_roundTripProtocolSession() {
        val session = SteamMobileSession(
            steamId = "76561198000000000",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            guardData = "guard-data",
            sessionId = "session-id",
            oauthToken = "oauth-token",
            cookies = listOf(
                SteamSessionCookie(name = "sessionid", value = "session-id"),
                SteamSessionCookie(name = "steamLoginSecure", value = "secure-cookie"),
            ),
        )

        val record = session.toRecord(
            tokenId = "token-1",
            accountName = "demo-account",
            createdAt = "2026-04-09T02:00:00Z",
            updatedAt = "2026-04-09T02:05:00Z",
        )

        assertEquals("access-token", record.accessToken)
        assertEquals("guard-data", record.guardData)
        assertEquals(SteamSessionPlatform.MOBILE_APP, record.platform)
        assertEquals(session, SteamMobileSession.fromRecord(record))
    }

    @Test
    fun fromRecord_returnsNullWhenProtocolTokensAreMissing() {
        val record = SteamSessionRecord(
            tokenId = "token-2",
            accountName = "web-only",
            steamId = "76561198000000001",
            sessionId = "session-id",
            cookies = listOf(SteamSessionCookie(name = "sessionid", value = "session-id")),
            createdAt = "2026-04-09T02:00:00Z",
            updatedAt = "2026-04-09T02:00:00Z",
        )

        assertNull(SteamMobileSession.fromRecord(record))
    }
}
