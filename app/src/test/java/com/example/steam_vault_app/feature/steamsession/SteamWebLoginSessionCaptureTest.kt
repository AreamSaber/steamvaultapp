package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SteamWebLoginSessionCaptureTest {
    @Test
    fun captureFromCookieHeader_extractsSessionAndSteamId() {
        val capture = SteamWebLoginSessionCapture.captureFromCookieHeader(
            cookieHeader = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Csecure-token; timezoneOffset=28800,0",
            currentUrl = "https://steamcommunity.com/id/demo/",
        )

        assertNotNull(capture)
        assertEquals("abc123", capture?.sessionId)
        assertEquals("76561198000000000", capture?.steamId)
        assertEquals("https://steamcommunity.com/id/demo/", capture?.currentUrl)
    }

    @Test
    fun captureFromCookieHeader_withoutAuthCookie_returnsNull() {
        val capture = SteamWebLoginSessionCapture.captureFromCookieHeader(
            cookieHeader = "sessionid=abc123; timezoneOffset=28800,0",
            currentUrl = "https://steamcommunity.com/",
        )

        assertNull(capture)
    }

    @Test
    fun captureFromCookieHeader_withoutSessionId_returnsNull() {
        val capture = SteamWebLoginSessionCapture.captureFromCookieHeader(
            cookieHeader = "steamLoginSecure=76561198000000000%7C%7Csecure-token",
            currentUrl = "https://steamcommunity.com/",
        )

        assertNull(capture)
    }

    @Test
    fun detectStage_withLoginCookies_returnsSessionReady() {
        val stage = SteamWebLoginSessionCapture.detectStage(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/id/demo/",
                cookieHeader = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Csecure-token",
                pageTitle = "Steam Community",
                pageTextSnippet = "Welcome back",
            ),
        )

        assertEquals(SteamWebLoginStage.SESSION_READY, stage)
    }

    @Test
    fun detectStage_withEmailSignals_returnsEmailCodeRequired() {
        val stage = SteamWebLoginSessionCapture.detectStage(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/checkemail",
                cookieHeader = "sessionid=abc123; timezoneOffset=28800,0",
                pageTitle = "Steam Sign In",
                pageTextSnippet = "Check your email for your Steam Guard code and enter it here.",
            ),
        )

        assertEquals(SteamWebLoginStage.EMAIL_CODE_REQUIRED, stage)
    }

    @Test
    fun detectStage_withTwoFactorSignals_returnsTwoFactorRequired() {
        val stage = SteamWebLoginSessionCapture.detectStage(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=",
                cookieHeader = "sessionid=abc123; timezoneOffset=28800,0",
                pageTitle = "Steam Guard",
                pageTextSnippet = "Enter the code from your mobile authenticator to continue.",
            ),
        )

        assertEquals(SteamWebLoginStage.TWO_FACTOR_REQUIRED, stage)
    }

    @Test
    fun detectStage_withCredentialSignals_returnsWaitingForCredentials() {
        val stage = SteamWebLoginSessionCapture.detectStage(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=",
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Sign In",
                pageTextSnippet = "Use your account name and password to sign in.",
            ),
        )

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, stage)
    }

    @Test
    fun analyzeObservation_withInvalidCredentials_detectsCredentialFailure() {
        val analysis = SteamWebLoginSessionCapture.analyzeObservation(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=",
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Steam Sign In",
                pageTextSnippet = "The account name or password that you have entered is incorrect.",
            ),
        )

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, analysis.stage)
        assertEquals(SteamWebLoginFailureReason.INVALID_CREDENTIALS, analysis.failureReason)
    }

    @Test
    fun analyzeObservation_withInvalidEmailCode_detectsEmailFailure() {
        val analysis = SteamWebLoginSessionCapture.analyzeObservation(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/checkemail",
                cookieHeader = "sessionid=abc123; timezoneOffset=28800,0",
                pageTitle = "Steam Guard",
                pageTextSnippet = "The code you entered is invalid. Check your email for a new Steam Guard code.",
            ),
        )

        assertEquals(SteamWebLoginStage.EMAIL_CODE_REQUIRED, analysis.stage)
        assertEquals(SteamWebLoginFailureReason.INVALID_EMAIL_CODE, analysis.failureReason)
    }

    @Test
    fun analyzeObservation_withRateLimit_detectsRateLimitedFailure() {
        val analysis = SteamWebLoginSessionCapture.analyzeObservation(
            SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=",
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Steam Sign In",
                pageTextSnippet = "There have been too many login failures from your network in a short time period. Please wait and try again later.",
            ),
        )

        assertEquals(SteamWebLoginFailureReason.RATE_LIMITED, analysis.failureReason)
    }
}
