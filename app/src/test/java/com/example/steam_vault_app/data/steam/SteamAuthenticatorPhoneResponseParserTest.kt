package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAuthenticatorPhoneResponseParserTest {
    @Test
    fun parseUserCountry_returnsCountryWhenPresent() {
        val response = """
            {
              "response": {
                "country": "CN"
              }
            }
        """.trimIndent()

        val country = SteamAuthenticatorPhoneResponseParser.parseUserCountry(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertEquals("CN", country)
    }

    @Test
    fun parsePhoneStatus_readsVerifiedPhoneFlag() {
        val response = """
            {
              "response": {
                "verified_phone": true
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorPhoneResponseParser.parsePhoneStatus(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.verifiedPhone)
    }

    @Test
    fun parseSetPhoneNumber_readsEmailAndFormattedNumber() {
        val response = """
            {
              "response": {
                "success": 1,
                "confirmation_email_address": "d***@example.com",
                "phone_number_formatted": "+86 138 0013 8000"
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorPhoneResponseParser.parseSetPhoneNumber(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertEquals("d***@example.com", result.confirmationEmailAddress)
        assertEquals("+86 138 0013 8000", result.phoneNumberFormatted)
    }

    @Test
    fun parsePhoneStatus_acceptsNumericBooleanFlags() {
        val response = """
            {
              "response": {
                "verified_phone": 1
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorPhoneResponseParser.parsePhoneStatus(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.verifiedPhone)
    }

    @Test
    fun parseEmailConfirmationStatus_readsPendingStateAndWaitTime() {
        val response = """
            {
              "response": {
                "awaiting_email_confirmation": true,
                "seconds_to_wait": 45
              }
            }
        """.trimIndent()

        val result = SteamAuthenticatorPhoneResponseParser.parseEmailConfirmationStatus(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertTrue(result.awaitingEmailConfirmation)
        assertEquals(45, result.secondsToWait)
    }

    @Test
    fun parseVerifyPhone_mapsOauthErrorToTypedFailure() {
        val response = """
            {
              "response": {
                "success": false,
                "message": "The access token is invalid or expired."
              }
            }
        """.trimIndent()

        val error = try {
            SteamAuthenticatorPhoneResponseParser.parseVerifyPhone(
                responseBody = response,
                defaultFailureMessage = "failed",
                invalidResponseMessage = "invalid response",
            )
            throw AssertionError("Expected parseVerifyPhone to throw")
        } catch (error: SteamAuthenticatorBindingException) {
            error
        }

        assertEquals(SteamAuthenticatorBindingFailureReason.OAUTH_INVALID, error.reason)
        assertEquals("The access token is invalid or expired.", error.message)
    }

    @Test
    fun parseUserCountry_returnsNullWhenCountryMissing() {
        val response = """
            {
              "response": {}
            }
        """.trimIndent()

        val country = SteamAuthenticatorPhoneResponseParser.parseUserCountry(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertNull(country)
    }

    @Test
    fun parsePhoneStatus_defaultsToUnverifiedWhenFlagMissing() {
        val response = """
            {
              "response": {}
            }
        """.trimIndent()

        val result = SteamAuthenticatorPhoneResponseParser.parsePhoneStatus(
            responseBody = response,
            defaultFailureMessage = "failed",
            invalidResponseMessage = "invalid response",
        )

        assertFalse(result.verifiedPhone)
    }
}
