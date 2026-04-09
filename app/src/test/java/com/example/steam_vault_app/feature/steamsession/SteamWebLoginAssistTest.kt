package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamWebLoginAssistTest {
    @Test
    fun supportsStage_onlyEmailAndTwoFactorReturnTrue() {
        assertTrue(SteamWebLoginAssist.supportsStage(SteamWebLoginStage.WAITING_FOR_CREDENTIALS))
        assertTrue(SteamWebLoginAssist.supportsStage(SteamWebLoginStage.EMAIL_CODE_REQUIRED))
        assertTrue(SteamWebLoginAssist.supportsStage(SteamWebLoginStage.TWO_FACTOR_REQUIRED))
        assertFalse(SteamWebLoginAssist.supportsStage(SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED))
        assertFalse(SteamWebLoginAssist.supportsStage(SteamWebLoginStage.SESSION_READY))
        assertFalse(SteamWebLoginAssist.supportsStage(SteamWebLoginStage.UNKNOWN))
    }

    @Test
    fun buildJavascript_credentialsStageContainsAccountAndPasswordHints() {
        val script = SteamWebLoginAssist.buildJavascript(
            SteamWebLoginAssistRequest(
                requestId = 1,
                stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                primaryValue = "demo-account",
                secondaryValue = "demo-password",
            ),
        )

        assertTrue(script.contains("\"accountname\""))
        assertTrue(script.contains("\"username\""))
        assertTrue(script.contains("\"password\""))
        assertTrue(script.contains("empty_account"))
        assertTrue(script.contains("empty_password"))
        assertTrue(script.contains("submit_not_ready"))
        assertTrue(script.contains("Object.getOwnPropertyDescriptor"))
        assertTrue(script.contains("HTMLInputElement.prototype"))
        assertTrue(script.contains("element.getClientRects"))
        assertTrue(script.contains("type === \"hidden\""))
        assertTrue(script.contains("current.parentElement"))
    }

    @Test
    fun buildJavascript_emailStageContainsEmailHints() {
        val script = SteamWebLoginAssist.buildJavascript(
            SteamWebLoginAssistRequest(
                requestId = 1,
                stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                primaryValue = "123456",
            ),
        )

        assertTrue(script.contains("\"emailauth\""))
        assertTrue(script.contains("\"email\""))
        assertFalse(script.contains("\"twofactor\""))
    }

    @Test
    fun buildJavascript_twoFactorStageContainsTwoFactorHints() {
        val script = SteamWebLoginAssist.buildJavascript(
            SteamWebLoginAssistRequest(
                requestId = 1,
                stage = SteamWebLoginStage.TWO_FACTOR_REQUIRED,
                primaryValue = "654321",
            ),
        )

        assertTrue(script.contains("\"twofactor\""))
        assertTrue(script.contains("\"authcode\""))
    }

    @Test
    fun parseJavascriptResult_decodesSuccessfulSubmission() {
        val result = SteamWebLoginAssist.parseJavascriptResult(
            "\"{\\\"success\\\":true,\\\"submitted\\\":true,\\\"matchedField\\\":\\\"emailauth\\\",\\\"message\\\":\\\"filled_and_submitted\\\"}\"",
        )

        assertTrue(result.success)
        assertTrue(result.submitted)
        assertEquals("emailauth", result.matchedField)
        assertEquals("filled_and_submitted", result.message)
    }

    @Test
    fun parseJavascriptResult_decodesFilledButNotReadySubmission() {
        val result = SteamWebLoginAssist.parseJavascriptResult(
            "\"{\\\"success\\\":true,\\\"submitted\\\":false,\\\"matchedField\\\":\\\"accountname|password\\\",\\\"message\\\":\\\"submit_not_ready\\\"}\"",
        )

        assertTrue(result.success)
        assertFalse(result.submitted)
        assertEquals("accountname|password", result.matchedField)
        assertEquals("submit_not_ready", result.message)
    }

    @Test
    fun parseJavascriptResult_handlesInvalidPayload() {
        val result = SteamWebLoginAssist.parseJavascriptResult("null")

        assertFalse(result.success)
        assertFalse(result.submitted)
        assertEquals("empty_result", result.message)
    }
}
