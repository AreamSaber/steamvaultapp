package com.example.steam_vault_app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGuardAccountSnapshotTest {
    @Test
    fun fromLegacy_buildsUnifiedSnapshotAndKeepsConversionStable() {
        val token = TokenRecord(
            id = "token-1",
            accountName = "demo-account",
            sharedSecret = "shared-secret",
            identitySecret = "identity-secret",
            serialNumber = "serial-1",
            revocationCode = "R12345",
            secret1 = "secret-1",
            deviceId = "android:demo-device",
            tokenGid = "token-gid-1",
            uri = "otpauth://steam/demo",
            createdAt = "2026-04-09T01:00:00Z",
            updatedAt = "2026-04-09T01:05:00Z",
        )
        val session = SteamSessionRecord(
            tokenId = "token-1",
            accountName = "demo-account",
            steamId = "76561198000000000",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            sessionId = "session-id",
            cookies = listOf(
                SteamSessionCookie(name = "sessionid", value = "session-id"),
                SteamSessionCookie(name = "steamLoginSecure", value = "steam-login-secure"),
            ),
            oauthToken = "oauth-token",
            platform = SteamSessionPlatform.MOBILE_APP,
            createdAt = "2026-04-09T01:00:00Z",
            updatedAt = "2026-04-09T01:06:00Z",
        )

        val snapshot = SteamGuardAccountSnapshot.fromLegacy(token = token, session = session)

        assertEquals("token-1", snapshot.tokenId)
        assertTrue(snapshot.hasProtocolSession)
        assertTrue(snapshot.hasWebConfirmationSession)
        assertEquals(token, snapshot.toTokenRecord())

        val importDraft = snapshot.toImportDraft()
        assertEquals(ImportSource.STEAM_BINDING, importDraft.source)
        assertEquals("demo-account", importDraft.accountName)
        assertEquals("shared-secret", importDraft.sharedSecret)
        assertEquals("identity-secret", importDraft.identitySecret)
        assertEquals("android:demo-device", importDraft.deviceId)
    }

    @Test
    fun hasProtocolSession_isFalseWhenOnlyLegacyWebSessionExists() {
        val snapshot = SteamGuardAccountSnapshot(
            tokenId = "token-2",
            accountName = "web-only",
            authenticator = SteamAuthenticatorSecrets(sharedSecret = "shared-secret"),
            session = SteamSessionRecord(
                tokenId = "token-2",
                accountName = "web-only",
                steamId = "76561198000000001",
                sessionId = "session-id",
                cookies = listOf(SteamSessionCookie(name = "sessionid", value = "session-id")),
                createdAt = "2026-04-09T01:00:00Z",
                updatedAt = "2026-04-09T01:00:00Z",
            ),
            createdAt = "2026-04-09T01:00:00Z",
            updatedAt = "2026-04-09T01:00:00Z",
        )

        assertFalse(snapshot.hasProtocolSession)
        assertTrue(snapshot.hasWebConfirmationSession)
    }
}
