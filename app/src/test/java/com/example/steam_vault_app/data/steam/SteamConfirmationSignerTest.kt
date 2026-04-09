package com.example.steam_vault_app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamConfirmationSignerTest {
    @Test
    fun generateConfirmationKey_matchesKnownVectorForConf() {
        val key = SteamConfirmationSigner.generateConfirmationKey(
            identitySecret = "c3RlYW0=",
            timestampSeconds = 1_700_000_000L,
            tag = "conf",
        )

        assertEquals("kHGqphXdeY3HpXcPbHejjeWy9qw=", key)
    }

    @Test
    fun generateConfirmationKey_matchesKnownVectorForAllow() {
        val key = SteamConfirmationSigner.generateConfirmationKey(
            identitySecret = "c3RlYW0=",
            timestampSeconds = 1_700_000_000L,
            tag = "allow",
        )

        assertEquals("vH3pfIxIxJecGgCCWHo2iBtQ45g=", key)
    }

    @Test
    fun generateConfirmationKey_supportsSyncedSteamTimeOffset() {
        val resolvedTimestamp = SteamTimeOffsetCalculator.applyOffset(
            deviceTimeSeconds = 1_700_000_000L,
            offsetSeconds = 10L,
        )
        val key = SteamConfirmationSigner.generateConfirmationKey(
            identitySecret = "c3RlYW0=",
            timestampSeconds = resolvedTimestamp,
            tag = "conf",
        )

        assertEquals("EPaP4r6zgqae5yrJ6hHlu7LF+gQ=", key)
    }

    @Test
    fun generateConfirmationKey_rejectsBlankTag() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamConfirmationSigner.generateConfirmationKey(
                identitySecret = "c3RlYW0=",
                timestampSeconds = 1_700_000_000L,
                tag = " ",
            )
        }
    }
}
