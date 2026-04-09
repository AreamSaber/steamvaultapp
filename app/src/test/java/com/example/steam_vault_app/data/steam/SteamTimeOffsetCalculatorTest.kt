package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamTimeOffsetCalculatorTest {
    @Test
    fun calculateOffsetSeconds_returnsPositiveOffset() {
        val offset = SteamTimeOffsetCalculator.calculateOffsetSeconds(
            serverTimeSeconds = 1_700_000_010L,
            deviceTimeSeconds = 1_700_000_000L,
        )

        assertEquals(10L, offset)
    }

    @Test
    fun applyOffset_returnsServerAlignedTime() {
        val resolved = SteamTimeOffsetCalculator.applyOffset(
            deviceTimeSeconds = 1_700_000_000L,
            offsetSeconds = -7L,
        )

        assertEquals(1_699_999_993L, resolved)
    }

    @Test
    fun calculateOffsetSeconds_rejectsNegativeServerTime() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamTimeOffsetCalculator.calculateOffsetSeconds(
                serverTimeSeconds = -1L,
                deviceTimeSeconds = 5L,
            )
        }
    }
}
