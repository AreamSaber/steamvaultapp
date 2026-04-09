package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionPlatform
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamSessionProfileCodecTest {
    @Test
    fun encodeAndDecode_roundTripsSessionCookies() {
        val profile = SteamSessionProfile(
            sessions = listOf(
                SteamSessionRecord(
                    tokenId = "token-1",
                    accountName = "demo-account",
                    steamId = "76561198000000000",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    guardData = "guard-data",
                    sessionId = "session-id",
                    cookies = listOf(
                        SteamSessionCookie(name = "sessionid", value = "abc123"),
                        SteamSessionCookie(name = "steamLoginSecure", value = "secure-cookie"),
                    ),
                    oauthToken = "oauth-token",
                    platform = SteamSessionPlatform.MOBILE_APP,
                    createdAt = "2026-04-07T19:00:00Z",
                    updatedAt = "2026-04-07T19:05:00Z",
                    lastValidatedAt = "2026-04-07T19:10:00Z",
                    validationStatus = SteamSessionValidationStatus.ERROR,
                    lastValidationErrorMessage = "Session expired",
                ),
            ),
        )

        val decoded = SteamSessionProfileCodec.decode(SteamSessionProfileCodec.encode(profile))

        assertEquals(profile, decoded)
    }

    @Test
    fun decode_rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamSessionProfileCodec.decode("""{"version":3,"sessions":[]}""")
        }
    }
}
