package com.example.steam_vault_app.data.importing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamImportedSessionParserTest {
    @Test
    fun parseMaFileSession_extractsTokensAndSynthesizesCookies() {
        val session = SteamImportedSessionParser.parseMaFileSession(
            JSONObject(
                """
                    {
                      "Session": {
                        "SteamID": "76561198000000001",
                        "AccessToken": "demo-access",
                        "RefreshToken": "demo-refresh",
                        "SessionID": "session-123"
                      }
                    }
                """.trimIndent(),
            ),
        )

        assertNotNull(session)
        session ?: return
        assertEquals("76561198000000001", session.steamId)
        assertEquals("demo-access", session.accessToken)
        assertEquals("demo-refresh", session.refreshToken)
        assertEquals("session-123", session.sessionId)
        assertEquals("demo-access", session.oauthToken)
        assertTrue(
            session.cookies.any { cookie ->
                cookie.name == "steamLoginSecure" &&
                    cookie.value.startsWith("76561198000000001%7C%7Cdemo-access")
            },
        )
        assertTrue(session.cookies.any { it.name == "sessionid" && it.value == "session-123" })
        assertTrue(session.cookies.any { it.name == "mobileClient" && it.value == "android" })
    }

    @Test
    fun parseMaFileSession_returnsNullWhenSessionIsMissing() {
        val session = SteamImportedSessionParser.parseMaFileSession(
            JSONObject(
                """
                    {
                      "shared_secret": "demo"
                    }
                """.trimIndent(),
            ),
        )

        assertNull(session)
    }
}
