package com.example.steam_vault_app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedSteamSessionDraftTest {
    @Test
    fun toRecord_preservesImportedSessionAndEnsuresSessionCookie() {
        val record = ImportedSteamSessionDraft(
            steamId = "76561198000000001",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            sessionId = "session-123",
            cookies = listOf(
                SteamSessionCookie("steamLoginSecure", "76561198000000001%7C%7Caccess-token"),
            ),
        ).toRecord(
            tokenId = "token-1",
            accountName = "demo-account",
            updatedAt = "2026-04-09T12:00:00Z",
        )

        assertEquals("token-1", record.tokenId)
        assertEquals("demo-account", record.accountName)
        assertEquals("76561198000000001", record.steamId)
        assertEquals("access-token", record.accessToken)
        assertEquals("refresh-token", record.refreshToken)
        assertEquals("session-123", record.sessionId)
        assertEquals("access-token", record.oauthToken)
        assertTrue(record.cookies.any { it.name == "sessionid" && it.value == "session-123" })
    }

    @Test
    fun toRecord_fallsBackToExistingSessionValues() {
        val existingSession = SteamSessionRecord(
            tokenId = "token-1",
            accountName = "demo-account",
            steamId = "76561198000000001",
            accessToken = "old-access",
            refreshToken = "old-refresh",
            sessionId = "old-session",
            cookies = listOf(SteamSessionCookie("steamLoginSecure", "old-cookie")),
            oauthToken = "old-oauth",
            createdAt = "2026-04-08T10:00:00Z",
            updatedAt = "2026-04-08T10:05:00Z",
        )

        val record = ImportedSteamSessionDraft(
            sessionId = "new-session",
        ).toRecord(
            tokenId = "token-1",
            accountName = "demo-account",
            existingSession = existingSession,
            updatedAt = "2026-04-09T12:00:00Z",
        )

        assertEquals("76561198000000001", record.steamId)
        assertEquals("old-access", record.accessToken)
        assertEquals("old-refresh", record.refreshToken)
        assertEquals("new-session", record.sessionId)
        assertEquals("old-oauth", record.oauthToken)
        assertEquals("2026-04-08T10:00:00Z", record.createdAt)
        assertEquals("2026-04-09T12:00:00Z", record.updatedAt)
        assertTrue(record.cookies.any { it.name == "sessionid" && it.value == "new-session" })
    }
}
