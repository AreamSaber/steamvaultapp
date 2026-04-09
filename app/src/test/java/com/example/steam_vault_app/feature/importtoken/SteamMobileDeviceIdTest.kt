package com.example.steam_vault_app.feature.importtoken

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamMobileDeviceIdTest {
    @Test
    fun fromSteamId_returnsStableAndroidStyleIdentifier() {
        val deviceId = SteamMobileDeviceId.fromSteamId("76561198000000001")

        assertEquals(
            "android:ca748b58-133d-73d0-adcd-109baa02c0fa",
            deviceId,
        )
    }
}
