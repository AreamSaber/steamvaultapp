package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingException
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamAuthenticatorBindingFailureGuidanceFactoryTest {
    @Test
    fun from_recommendsReLoginWhenSessionExpired() {
        val guidance = SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = null,
            errorMessage = "Steam session has expired. Please log in again.",
            phase = SteamAuthenticatorBindingFailurePhase.BEGIN,
            currentMode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
            availableModes = listOf(SteamAuthenticatorBindingAuthMode.OAUTH_ONLY),
        )

        assertEquals(
            SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN,
            guidance?.kind,
        )
        assertNull(guidance?.suggestedMode)
    }

    @Test
    fun from_recommendsDifferentModeForOauthIssue() {
        val guidance = SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = null,
            errorMessage = "Invalid access token.",
            phase = SteamAuthenticatorBindingFailurePhase.FINALIZE,
            currentMode = SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            availableModes = listOf(
                SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY,
                SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            ),
        )

        assertEquals(
            SteamAuthenticatorBindingFailureGuidanceKind.SWITCH_AUTH_MODE,
            guidance?.kind,
        )
        assertEquals(
            SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY,
            guidance?.suggestedMode,
        )
    }

    @Test
    fun from_recommendsCheckingActivationCode() {
        val guidance = SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = null,
            errorMessage = "bad activation code",
            phase = SteamAuthenticatorBindingFailurePhase.FINALIZE,
            currentMode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
            availableModes = listOf(SteamAuthenticatorBindingAuthMode.OAUTH_ONLY),
        )

        assertEquals(
            SteamAuthenticatorBindingFailureGuidanceKind.CHECK_ACTIVATION_CODE,
            guidance?.kind,
        )
    }

    @Test
    fun from_prefersTypedFailureReasonWhenAvailable() {
        val guidance = SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = SteamAuthenticatorBindingException(
                reason = SteamAuthenticatorBindingFailureReason.WEB_API_KEY_INVALID,
                message = "publisher key required",
            ),
            errorMessage = "unexpected text",
            phase = SteamAuthenticatorBindingFailurePhase.BEGIN,
            currentMode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
            availableModes = listOf(
                SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY,
                SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            ),
        )

        assertEquals(
            SteamAuthenticatorBindingFailureGuidanceKind.SWITCH_AUTH_MODE,
            guidance?.kind,
        )
        assertEquals(
            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            guidance?.suggestedMode,
        )
    }

    @Test
    fun from_recommendsCompatibilityImportWhenAuthenticatorAlreadyPresent() {
        val guidance = SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = SteamAuthenticatorBindingException(
                reason = SteamAuthenticatorBindingFailureReason.AUTHENTICATOR_ALREADY_PRESENT,
                message = "authenticator already present",
            ),
            errorMessage = "unexpected text",
            phase = SteamAuthenticatorBindingFailurePhase.BEGIN,
            currentMode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
            availableModes = listOf(
                SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY,
            ),
        )

        assertEquals(
            SteamAuthenticatorBindingFailureGuidanceKind.OPEN_COMPATIBILITY_IMPORT,
            guidance?.kind,
        )
        assertNull(guidance?.suggestedMode)
    }

    @Test
    fun from_returnsNullForUnknownMessage() {
        val guidance = SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = null,
            errorMessage = "unexpected binding failure",
            phase = SteamAuthenticatorBindingFailurePhase.BEGIN,
            currentMode = SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
            availableModes = listOf(SteamAuthenticatorBindingAuthMode.OAUTH_ONLY),
        )

        assertNull(guidance)
    }
}
