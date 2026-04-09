package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginResult
import com.example.steam_vault_app.domain.model.ImportSource
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamAuthenticatorBindingImportDraftFactoryTest {
    @Test
    fun from_mapsBindingMaterialIntoImportDraft() {
        val draft = SteamAuthenticatorEnrollmentDraft(
            steamId = "76561198000000001",
            sessionId = "session-id",
            cookiesText = """
                sessionid=session-id
                steamLoginSecure=76561198000000001||secure
                oauth_token=oauth-123
            """.trimIndent(),
            currentUrl = "https://steamcommunity.com/",
            capturedAt = "2026-04-08T12:00:00Z",
            oauthToken = "oauth-123",
        )
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(draft)
        val beginResult = SteamAuthenticatorBeginResult(
            steamId = "76561198000000001",
            accountName = "Test Account",
            sharedSecret = "AQIDBA==",
            identitySecret = "BQYHCA==",
            serialNumber = "SERIAL-001",
            revocationCode = "R12345",
            secret1 = "CgsMDQ==",
            tokenGid = "TOKEN-GID",
            uri = "otpauth://steam/Test",
            rawResponse = "{\"response\":{}}",
        )

        val importDraft = SteamAuthenticatorBindingImportDraftFactory.from(
            preparation = preparation,
            beginResult = beginResult,
        )

        assertEquals(ImportSource.STEAM_BINDING, importDraft.source)
        assertEquals("Test Account", importDraft.accountName)
        assertEquals("AQIDBA==", importDraft.sharedSecret)
        assertEquals("BQYHCA==", importDraft.identitySecret)
        assertEquals("SERIAL-001", importDraft.serialNumber)
        assertEquals("R12345", importDraft.revocationCode)
        assertEquals("CgsMDQ==", importDraft.secret1)
        assertEquals("TOKEN-GID", importDraft.tokenGid)
        assertEquals("otpauth://steam/Test", importDraft.uri)
        assertEquals(
            SteamMobileDeviceId.fromSteamId("76561198000000001"),
            importDraft.deviceId,
        )
        assertEquals("{\"response\":{}}", importDraft.rawPayload)
    }

    @Test
    fun from_fallsBackToSteamIdWhenAccountNameMissing() {
        val draft = SteamAuthenticatorEnrollmentDraft(
            steamId = "76561198000000001",
            sessionId = "session-id",
            cookiesText = """
                sessionid=session-id
                steamLoginSecure=76561198000000001||secure
                oauth_token=oauth-123
            """.trimIndent(),
            currentUrl = "https://steamcommunity.com/",
            capturedAt = "2026-04-08T12:00:00Z",
            oauthToken = "oauth-123",
        )
        val preparation = SteamAuthenticatorBindingPreparationFactory.from(draft)
        val beginResult = SteamAuthenticatorBeginResult(
            steamId = "76561198000000001",
            sharedSecret = "AQIDBA==",
        )

        val importDraft = SteamAuthenticatorBindingImportDraftFactory.from(
            preparation = preparation,
            beginResult = beginResult,
        )

        assertEquals("Steam 76561198000000001", importDraft.accountName)
        assertNull(importDraft.identitySecret)
    }
}
