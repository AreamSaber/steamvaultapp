package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginResult
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressDraft
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SteamAuthenticatorBindingProgressDraftCodecTest {
    @Test
    fun encodeAndDecode_roundTripsBindingProgress() {
        val draft = SteamAuthenticatorBindingProgressDraft(
            enrollmentDraftSignature = "draft-signature",
            begunAt = "2026-04-08T13:00:00Z",
            serverTimeOffsetSeconds = 12L,
            stage = SteamAuthenticatorBindingProgressStage.WAITING_NEXT_ACTIVATION_CODE,
            lastUpdatedAt = "2026-04-08T13:05:00Z",
            statusMessage = "Waiting for the next activation code",
            beginResult = SteamAuthenticatorBeginResult(
                steamId = "76561198000000001",
                accountName = "Test Account",
                sharedSecret = "AQIDBA==",
                identitySecret = "BQYHCA==",
                serialNumber = "SERIAL-001",
                revocationCode = "R12345",
                secret1 = "CgsMDQ==",
                tokenGid = "TOKEN-GID",
                uri = "otpauth://steam/Test",
                serverTimeSeconds = 1700000000L,
                status = 1,
                fullyEnrolled = false,
                rawResponse = "{\"response\":{}}",
            ),
        )

        val encoded = SteamAuthenticatorBindingProgressDraftCodec.encode(draft)
        val decoded = SteamAuthenticatorBindingProgressDraftCodec.decode(encoded)

        assertEquals("draft-signature", decoded.enrollmentDraftSignature)
        assertEquals("2026-04-08T13:00:00Z", decoded.begunAt)
        assertEquals(12L, decoded.serverTimeOffsetSeconds)
        assertEquals(
            SteamAuthenticatorBindingProgressStage.WAITING_NEXT_ACTIVATION_CODE,
            decoded.stage,
        )
        assertEquals("2026-04-08T13:05:00Z", decoded.lastUpdatedAt)
        assertEquals("Waiting for the next activation code", decoded.statusMessage)
        assertEquals("76561198000000001", decoded.beginResult.steamId)
        assertEquals("Test Account", decoded.beginResult.accountName)
        assertEquals("AQIDBA==", decoded.beginResult.sharedSecret)
        assertEquals("BQYHCA==", decoded.beginResult.identitySecret)
        assertEquals("SERIAL-001", decoded.beginResult.serialNumber)
        assertEquals("R12345", decoded.beginResult.revocationCode)
        assertEquals("CgsMDQ==", decoded.beginResult.secret1)
        assertEquals("TOKEN-GID", decoded.beginResult.tokenGid)
        assertEquals("otpauth://steam/Test", decoded.beginResult.uri)
        assertEquals(1700000000L, decoded.beginResult.serverTimeSeconds)
        assertEquals(1, decoded.beginResult.status)
        assertFalse(decoded.beginResult.fullyEnrolled)
        assertEquals("{\"response\":{}}", decoded.beginResult.rawResponse)
    }
}
