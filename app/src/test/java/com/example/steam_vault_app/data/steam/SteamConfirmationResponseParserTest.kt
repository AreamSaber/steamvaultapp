package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamConfirmationResponseParserTest {
    @Test
    fun parseConfirmationList_parsesValidItemsAndSkipsBrokenEntries() {
        val confirmations = SteamConfirmationResponseParser.parseConfirmationList(
            responseBody = """
                {
                  "success": true,
                  "conf": [
                    {
                      "id": "1001",
                      "nonce": "nonce-1001",
                      "type": 2,
                      "headline": "",
                      "type_name": "Trade Offer",
                      "summary": [
                        "Knife trade",
                        { "value": "From: Demo" },
                        { "text": "To: Vault" },
                        ""
                      ],
                      "warn": "Requires approval",
                      "creator_id": "76561198000000001",
                      "creation_time": 1700000000,
                      "multi": true,
                      "icon": "https://example.com/icon.png"
                    },
                    {
                      "id": "broken-missing-nonce"
                    },
                    "unexpected"
                  ]
                }
            """.trimIndent(),
            defaultFailureMessage = "failed",
        )

        assertEquals(1, confirmations.size)
        val confirmation = confirmations.single()
        assertEquals("1001", confirmation.id)
        assertEquals("nonce-1001", confirmation.nonce)
        assertEquals(2, confirmation.typeCode)
        assertEquals("Trade Offer", confirmation.headline)
        assertEquals(listOf("Knife trade", "From: Demo", "To: Vault"), confirmation.summary)
        assertEquals("Requires approval", confirmation.warn)
        assertEquals("76561198000000001", confirmation.creatorId)
        assertEquals(1_700_000_000L, confirmation.creationTimeEpochSeconds)
        assertTrue(confirmation.isMulti)
        assertEquals("https://example.com/icon.png", confirmation.iconUrl)
    }

    @Test
    fun parseConfirmationList_invalidJsonFallsBackToDefaultMessage() {
        val error = assertThrows(IllegalStateException::class.java) {
            SteamConfirmationResponseParser.parseConfirmationList(
                responseBody = "<html>expired</html>",
                defaultFailureMessage = "failed",
            )
        }

        assertEquals("failed", error.message)
    }

    @Test
    fun ensureActionSucceeded_prefersServerMessageOnFailure() {
        val error = assertThrows(IllegalStateException::class.java) {
            SteamConfirmationResponseParser.ensureActionSucceeded(
                responseBody = """
                    {
                      "success": false,
                      "message": "Session expired"
                    }
                """.trimIndent(),
                defaultFailureMessage = "failed",
            )
        }

        assertEquals("Session expired", error.message)
    }

    @Test
    fun parseConfirmationList_supportsNumericSuccessAndMultiFlags() {
        val confirmations = SteamConfirmationResponseParser.parseConfirmationList(
            responseBody = """
                {
                  "success": 1,
                  "conf": [
                    {
                      "id": "2001",
                      "nonce": "nonce-2001",
                      "headline": "Trade Offer",
                      "multi": 1
                    }
                  ]
                }
            """.trimIndent(),
            defaultFailureMessage = "failed",
        )

        assertEquals(1, confirmations.size)
        assertTrue(confirmations.single().isMulti)
    }

    @Test
    fun ensureActionSucceeded_needAuthFailsEvenWhenSuccessIsTrue() {
        val error = assertThrows(IllegalStateException::class.java) {
            SteamConfirmationResponseParser.ensureActionSucceeded(
                responseBody = """
                    {
                      "success": true,
                      "needauth": 1,
                      "message": "Needs Authentication"
                    }
                """.trimIndent(),
                defaultFailureMessage = "failed",
            )
        }

        assertEquals("Needs Authentication", error.message)
    }
}
