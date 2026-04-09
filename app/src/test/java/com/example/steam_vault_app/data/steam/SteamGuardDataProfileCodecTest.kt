package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamGuardDataRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamGuardDataProfileCodecTest {
    @Test
    fun encodeAndDecode_roundTripsRecords() {
        val profile = SteamGuardDataProfile(
            records = listOf(
                SteamGuardDataRecord(
                    accountName = "demo-account",
                    steamId = "76561198000000000",
                    guardData = "guard-data",
                    updatedAt = "2026-04-09T02:00:00Z",
                ),
            ),
        )

        val decoded = SteamGuardDataProfileCodec.decode(SteamGuardDataProfileCodec.encode(profile))

        assertEquals(profile, decoded)
    }

    @Test
    fun decode_rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamGuardDataProfileCodec.decode("""{"version":2,"records":[]}""")
        }
    }
}
