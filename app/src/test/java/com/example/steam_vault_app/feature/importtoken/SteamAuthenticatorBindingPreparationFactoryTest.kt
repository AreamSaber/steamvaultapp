package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAuthenticatorBindingPreparationFactoryTest {
    @Test
    fun from_marksDraftReadyWhenRequiredCookiesExist() {
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(
            draft(
                steamId = "76561198000000001",
                cookies = """
                    sessionid=session-cookie
                    steamLoginSecure=76561198000000001%7C%7Csecure-cookie
                """.trimIndent(),
                oauthToken = "oauth-token-123",
            ),
        )

        assertTrue(preparation.isReadyForBinding)
        assertEquals("76561198000000001", preparation.resolvedSteamId)
        assertEquals("oauth-token-123", preparation.oauthToken)
        assertNotNull(preparation.sessionIdCookie)
        assertNotNull(preparation.steamLoginSecureCookie)
        assertEquals(
            "android:ca748b58-133d-73d0-adcd-109baa02c0fa",
            preparation.generatedDeviceId,
        )
        assertTrue(preparation.missingRequirements.isEmpty())
    }

    @Test
    fun from_marksDraftReadyWhenWebApiKeyExistsWithoutOauthToken() {
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(
            draft(
                steamId = "76561198000000003",
                cookies = """
                    sessionid=session-cookie
                    steamLoginSecure=76561198000000003%7C%7Csecure-cookie
                """.trimIndent(),
                webApiKey = "web-api-key-123",
            ),
        )

        assertTrue(preparation.isReadyForBinding)
        assertEquals("76561198000000003", preparation.resolvedSteamId)
        assertEquals("web-api-key-123", preparation.webApiKey)
        assertEquals(null, preparation.oauthToken)
        assertTrue(preparation.missingRequirements.isEmpty())
    }

    @Test
    fun from_infersSteamIdFromSteamLoginSecureCookie() {
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(
            draft(
                cookies = """
                    sessionid=session-cookie
                    steamLoginSecure=76561198000000002%7C%7Csecure-cookie
                    oauth_token=oauth-token-456
                """.trimIndent(),
            ),
        )

        assertEquals("76561198000000002", preparation.resolvedSteamId)
        assertEquals("oauth-token-456", preparation.oauthToken)
        assertTrue(preparation.isReadyForBinding)
    }

    @Test
    fun from_marksMissingRequirements() {
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(
            draft(
                cookies = "steamRememberLogin=1",
            ),
        )

        assertFalse(preparation.isReadyForBinding)
        assertNull(preparation.sessionIdCookie)
        assertNull(preparation.steamLoginSecureCookie)
        assertTrue(
            SteamAuthenticatorBindingRequirement.SESSION_ID_COOKIE in preparation.missingRequirements,
        )
        assertTrue(
            SteamAuthenticatorBindingRequirement.STEAM_LOGIN_SECURE_COOKIE in preparation.missingRequirements,
        )
        assertTrue(
            SteamAuthenticatorBindingRequirement.STEAM_ID in preparation.missingRequirements,
        )
        assertTrue(
            SteamAuthenticatorBindingRequirement.API_AUTHENTICATION in preparation.missingRequirements,
        )
    }

    @Test
    fun from_protocolContext_marksStructuredSessionReady() {
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(
            SteamAuthenticatorBindingContext(
                accountName = "demo-account",
                session = SteamMobileSession(
                    steamId = "76561198000000004",
                    accessToken = "access-token-123",
                    refreshToken = "refresh-token-456",
                    guardData = "guard-data-789",
                    sessionId = "session-cookie",
                    oauthToken = "oauth-token-123",
                    cookies = listOf(
                        SteamSessionCookie(name = "sessionid", value = "session-cookie"),
                        SteamSessionCookie(
                            name = "steamLoginSecure",
                            value = "76561198000000004%7C%7Csecure-cookie",
                        ),
                    ),
                ),
                capturedAt = "2026-04-09T12:34:56Z",
            ),
        )

        assertTrue(preparation.isReadyForBinding)
        assertEquals("demo-account", preparation.accountName)
        assertEquals("session-cookie", preparation.sessionId)
        assertEquals("76561198000000004", preparation.resolvedSteamId)
        assertEquals("oauth-token-123", preparation.oauthToken)
        assertNull(preparation.draft)
        assertNotNull(preparation.bindingContext)
    }

    private fun draft(
        steamId: String? = null,
        cookies: String,
        oauthToken: String? = null,
        webApiKey: String? = null,
    ): SteamAuthenticatorEnrollmentDraft {
        return SteamAuthenticatorEnrollmentDraft(
            steamId = steamId,
            sessionId = "session-cookie",
            cookiesText = cookies,
            currentUrl = "https://steamcommunity.com/login/home/",
            capturedAt = "2026-04-08T12:34:56Z",
            oauthToken = oauthToken,
            webApiKey = webApiKey,
        )
    }
}
