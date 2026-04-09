package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAuthenticatorBindingResponseParserTest {
    @Test
    fun parseBeginResult_normalizesSecretsAndMetadata() {
        val response = """
            {
              "response": {
                "success": 1,
                "account_name": "Test Account",
                "shared_secret": "AQIDBA==",
                "identity_secret": "BQYHCA==",
                "secret_1": "CgsMDQ==",
                "serial_number": "SERIAL-001",
                "revocation_code": "R12345",
                "token_gid": "TOKEN-GID",
                "uri": "otpauth://steam/Test",
                "server_time": 1700000000,
                "status": 1,
                "fully_enrolled": 0
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorBindingResponseParser.parseBeginResult(
            responseBody = response,
            steamId = "76561198000000001",
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
            phoneNumberRequiredMessage = "phone required",
            authenticatorPresentMessage = "authenticator present",
        )

        assertEquals("76561198000000001", result.steamId)
        assertEquals("Test Account", result.accountName)
        assertEquals("AQIDBA==", result.sharedSecret)
        assertEquals("BQYHCA==", result.identitySecret)
        assertEquals("CgsMDQ==", result.secret1)
        assertEquals("SERIAL-001", result.serialNumber)
        assertEquals("R12345", result.revocationCode)
        assertEquals("TOKEN-GID", result.tokenGid)
        assertEquals("otpauth://steam/Test", result.uri)
        assertEquals(1700000000L, result.serverTimeSeconds)
        assertEquals(1, result.status)
        assertFalse(result.fullyEnrolled)
    }

    @Test
    fun parseFinalizeResult_marksPendingWhenMoreActivationRequired() {
        val response = """
            {
              "response": {
                "status": 2,
                "want_more": 1,
                "server_time": 1700000100
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorBindingResponseParser.parseFinalizeResult(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.success)
        assertEquals(2, result.status)
        assertEquals(1700000100L, result.serverTimeSeconds)
        assertTrue(result.wantsMoreActivation)
    }

    @Test
    fun parseFinalizeResult_doesNotTreatStatus2AsPendingWhenSteamMarkedSuccess() {
        val response = """
            {
              "response": {
                "success": 1,
                "status": 2,
                "server_time": 1700000150
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorBindingResponseParser.parseFinalizeResult(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.success)
        assertEquals(2, result.status)
        assertEquals(1700000150L, result.serverTimeSeconds)
        assertFalse(result.wantsMoreActivation)
    }

    @Test
    fun parseFinalizeResult_treatsStatus88AsPendingEvenWithoutWantMoreFlag() {
        val response = """
            {
              "response": {
                "status": 88,
                "server_time": 1700000200
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorBindingResponseParser.parseFinalizeResult(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.success)
        assertEquals(88, result.status)
        assertEquals(1700000200L, result.serverTimeSeconds)
        assertTrue(result.wantsMoreActivation)
    }

    @Test
    fun parseFinalizeResult_prefersPendingStatusOverRootSuccessFlag() {
        val response = """
            {
              "success": false,
              "response": {
                "status": 2,
                "server_time": 1700000300
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorBindingResponseParser.parseFinalizeResult(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.success)
        assertEquals(2, result.status)
        assertEquals(1700000300L, result.serverTimeSeconds)
        assertTrue(result.wantsMoreActivation)
    }

    @Test
    fun parseBeginResult_mapsMissingPhoneStatusToTypedFailure() {
        val response = """
            {
              "response": {
                "status": 2
              }
            }
        """.trimIndent()

        val error = try {
            SteamAuthenticatorBindingResponseParser.parseBeginResult(
                responseBody = response,
                steamId = "76561198000000002",
                defaultFailureMessage = "failed",
                invalidResponseMessage = "invalid response",
                phoneNumberRequiredMessage = "phone required",
                authenticatorPresentMessage = "authenticator present",
            )
            throw AssertionError("Expected parseBeginResult to throw")
        } catch (error: SteamAuthenticatorBindingException) {
            error
        }

        assertEquals(SteamAuthenticatorBindingFailureReason.PHONE_NUMBER_REQUIRED, error.reason)
        assertEquals(2, error.steamStatusCode)
        assertEquals("phone required", error.message)
    }

    @Test
    fun parseFinalizeResult_mapsBadActivationCodeToTypedFailure() {
        val response = """
            {
              "success": false,
              "response": {
                "status": 89,
                "message": "bad activation code"
              }
            }
        """.trimIndent()

        val error = try {
            SteamAuthenticatorBindingResponseParser.parseFinalizeResult(
                responseBody = response,
                defaultFailureMessage = "failed",
                invalidResponseMessage = "invalid response",
            )
            throw AssertionError("Expected parseFinalizeResult to throw")
        } catch (error: SteamAuthenticatorBindingException) {
            error
        }

        assertEquals(SteamAuthenticatorBindingFailureReason.ACTIVATION_CODE_INVALID, error.reason)
        assertEquals(89, error.steamStatusCode)
        assertEquals("bad activation code", error.message)
    }
}
