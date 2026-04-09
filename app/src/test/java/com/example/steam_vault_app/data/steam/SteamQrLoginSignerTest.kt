package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamQrLoginSignerTest {
    @Test
    fun generateSignature_usesLittleEndianPayloadWithSharedSecret() {
        val signature = SteamQrLoginSigner.generateSignature(
            sharedSecret = "c2hhcmVkLXNlY3JldA==",
            version = 7,
            clientId = 123456789uL,
            steamId = 76561198000000000L,
        )

        assertEquals(
            "941a44f47c6a32abb1a03190f2a59d9abad7ef8d687d914924e0b099956f2de9",
            signature.joinToString(separator = "") { byte -> "%02x".format(byte) },
        )
    }
}
