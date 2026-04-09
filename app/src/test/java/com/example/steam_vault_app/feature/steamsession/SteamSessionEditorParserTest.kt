package com.example.steam_vault_app.feature.steamsession

import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSessionEditorParserTest {
    @Test
    fun buildSessionRecord_mergesSessionIdIntoCookiesAndPreservesCreatedAt() {
        val existing = SteamSessionRecord(
            tokenId = "token-1",
            accountName = "demo",
            steamId = "76561198000000000",
            sessionId = "old-session",
            cookies = listOf(SteamSessionCookie("steamLoginSecure", "legacy-cookie")),
            oauthToken = "legacy-oauth",
            createdAt = "2026-04-07T10:00:00Z",
            updatedAt = "2026-04-07T10:05:00Z",
            lastValidatedAt = "2026-04-07T10:06:00Z",
            validationStatus = SteamSessionValidationStatus.SUCCESS,
            lastValidationErrorMessage = "old error",
        )

        val updated = SteamSessionEditorParser.buildSessionRecord(
            tokenId = "token-1",
            accountName = "demo",
            existingSession = existing,
            steamIdInput = "76561198011111111",
            sessionIdInput = "fresh-session",
            oauthTokenInput = "fresh-oauth",
            rawCookiesInput = "steamLoginSecure=secure-cookie",
            nowProvider = { "2026-04-07T12:00:00Z" },
        )

        assertEquals("2026-04-07T10:00:00Z", updated.createdAt)
        assertEquals("2026-04-07T12:00:00Z", updated.updatedAt)
        assertNull(updated.lastValidatedAt)
        assertEquals(SteamSessionValidationStatus.UNKNOWN, updated.validationStatus)
        assertNull(updated.lastValidationErrorMessage)
        assertEquals("fresh-session", updated.sessionId)
        assertEquals("fresh-oauth", updated.oauthToken)
        assertEquals(
            listOf(
                SteamSessionCookie("steamLoginSecure", "secure-cookie"),
                SteamSessionCookie("sessionid", "fresh-session"),
            ),
            updated.cookies,
        )
    }

    @Test
    fun buildSessionRecord_withoutSessionMaterial_throws() {
        val error = runCatching {
            SteamSessionEditorParser.buildSessionRecord(
                tokenId = "token-1",
                accountName = "demo",
                existingSession = null,
                steamIdInput = "76561198011111111",
                sessionIdInput = "",
                oauthTokenInput = "",
                rawCookiesInput = "",
            )
        }.exceptionOrNull()

        assertTrue(error is SteamSessionEditorException)
        assertEquals(
            SteamSessionEditorError.SESSION_MATERIAL_MISSING,
            (error as SteamSessionEditorException).error,
        )
    }

    @Test
    fun buildSessionRecord_withMalformedCookie_reportsLineNumber() {
        val error = runCatching {
            SteamSessionEditorParser.buildSessionRecord(
                tokenId = "token-1",
                accountName = "demo",
                existingSession = null,
                steamIdInput = "",
                sessionIdInput = "session-id",
                oauthTokenInput = "",
                rawCookiesInput = "steamLoginSecure\nsessionid=session-id",
            )
        }.exceptionOrNull()

        assertTrue(error is SteamSessionEditorException)
        assertEquals(
            SteamSessionEditorError.COOKIE_MALFORMED,
            (error as SteamSessionEditorException).error,
        )
        assertEquals(1, error.lineNumber)
    }

    @Test
    fun buildSessionRecord_normalizesBlankOptionalFieldsToNull() {
        val created = SteamSessionEditorParser.buildSessionRecord(
            tokenId = "token-1",
            accountName = "demo",
            existingSession = null,
            steamIdInput = " ",
            sessionIdInput = "session-id",
            oauthTokenInput = " ",
            rawCookiesInput = "",
            nowProvider = { "2026-04-07T12:00:00Z" },
        )

        assertNull(created.steamId)
        assertNull(created.oauthToken)
        assertEquals("2026-04-07T12:00:00Z", created.createdAt)
        assertEquals("2026-04-07T12:00:00Z", created.updatedAt)
        assertEquals(
            listOf(SteamSessionCookie("sessionid", "session-id")),
            created.cookies,
        )
    }
}
