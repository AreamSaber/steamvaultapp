package com.example.steam_vault_app.data.security

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamSecretCodecTest {
    @Test
    fun normalizeToBase64_acceptsBase32Secret() {
        val normalized = SteamSecretCodec.normalizeToBase64("ON2GKYLN")

        assertEquals("c3RlYW0=", normalized)
    }

    @Test
    fun decode_acceptsBase64WithWhitespace() {
        val decoded = SteamSecretCodec.decode("c3 RlY W0=\n")

        assertArrayEquals("steam".toByteArray(StandardCharsets.UTF_8), decoded)
    }

    @Test
    fun decode_rejectsInvalidSecret() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamSecretCodec.decode("not-a-valid-secret@@")
        }
    }
}
