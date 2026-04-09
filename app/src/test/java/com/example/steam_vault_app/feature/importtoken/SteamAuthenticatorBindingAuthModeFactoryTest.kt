package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SteamAuthenticatorBindingAuthModeFactoryTest {
    @Test
    fun availableModes_returnsSingleAndCombinedModes() {
        val preparation = preparation(
            oauthToken = "oauth-token-123",
            webApiKey = "web-api-key-456",
        )

        assertEquals(
            listOf(
                SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY,
                SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            ),
            SteamAuthenticatorBindingAuthModeFactory.availableModes(preparation),
        )
        assertEquals(
            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            SteamAuthenticatorBindingAuthModeFactory.preferredMode(preparation),
        )
    }

    @Test
    fun resolve_returnsOnlySelectedCredentials() {
        val preparation = preparation(
            oauthToken = "oauth-token-123",
            webApiKey = "web-api-key-456",
        )

        assertEquals(
            SteamAuthenticatorBindingResolvedAuth(
                oauthToken = "oauth-token-123",
                webApiKey = null,
            ),
            SteamAuthenticatorBindingAuthModeFactory.resolve(
                mode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                preparation = preparation,
            ),
        )
        assertEquals(
            SteamAuthenticatorBindingResolvedAuth(
                oauthToken = null,
                webApiKey = "web-api-key-456",
            ),
            SteamAuthenticatorBindingAuthModeFactory.resolve(
                mode = SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY,
                preparation = preparation,
            ),
        )
        assertEquals(
            SteamAuthenticatorBindingResolvedAuth(
                oauthToken = "oauth-token-123",
                webApiKey = "web-api-key-456",
            ),
            SteamAuthenticatorBindingAuthModeFactory.resolve(
                mode = SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
                preparation = preparation,
            ),
        )
    }

    @Test
    fun resolve_rejectsUnavailableMode() {
        val preparation = preparation(
            oauthToken = null,
            webApiKey = "web-api-key-456",
        )

        try {
            SteamAuthenticatorBindingAuthModeFactory.resolve(
                mode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                preparation = preparation,
            )
            fail("Expected resolve() to reject unavailable auth mode.")
        } catch (_: IllegalArgumentException) {
            // Expected path.
        }
    }

    private fun preparation(
        oauthToken: String?,
        webApiKey: String?,
    ): SteamAuthenticatorBindingPreparation {
        val draft = SteamAuthenticatorEnrollmentDraft(
            steamId = "76561198000000000",
            sessionId = "session-id-123",
            cookiesText = "sessionid=session-id-123; steamLoginSecure=76561198000000000%7C%7Ctoken",
            currentUrl = "https://steamcommunity.com",
            capturedAt = "2026-04-08T08:00:00Z",
            oauthToken = oauthToken,
            webApiKey = webApiKey,
        )
        return SteamAuthenticatorBindingPreparationFactory.from(draft)
    }
}
