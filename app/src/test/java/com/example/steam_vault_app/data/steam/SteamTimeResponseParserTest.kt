package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamTimeResponseParserTest {
    @Test
    fun parseServerTimeSeconds_acceptsNestedResponsePayload() {
        val seconds = SteamTimeResponseParser.parseServerTimeSeconds(
            """{"response":{"server_time":"1712500000"}}""",
        )

        assertEquals(1_712_500_000L, seconds)
    }

    @Test
    fun parseServerTimeSeconds_acceptsTopLevelNumber() {
        val seconds = SteamTimeResponseParser.parseServerTimeSeconds(
            """{"server_time":1712500001}""",
        )

        assertEquals(1_712_500_001L, seconds)
    }

    @Test
    fun parseServerTimeSeconds_rejectsMissingServerTime() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamTimeResponseParser.parseServerTimeSeconds("""{"response":{}}""")
        }
    }
}
