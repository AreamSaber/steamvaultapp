package com.example.steam_vault_app.feature.importtoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportTokenEntryContextTest {
    @Test
    fun existingAuthenticator_prefersExplicitAccountName() {
        val context = ImportTokenEntryContext.existingAuthenticator(
            accountName = " demo-account ",
            steamId = "76561198000000001",
        )

        assertEquals(ImportTokenEntryContext.Kind.EXISTING_AUTHENTICATOR, context.kind)
        assertEquals("demo-account", context.preferredAccountName)
        assertEquals("76561198000000001", context.steamId)
    }

    @Test
    fun existingAuthenticator_fallsBackToSteamIdLabel() {
        val context = ImportTokenEntryContext.existingAuthenticator(
            accountName = "   ",
            steamId = "76561198000000002",
        )

        assertEquals("Steam 76561198000000002", context.preferredAccountName)
    }

    @Test
    fun existingAuthenticator_leavesPrefillEmptyWhenNoContextExists() {
        val context = ImportTokenEntryContext.existingAuthenticator(
            accountName = null,
            steamId = null,
        )

        assertNull(context.preferredAccountName)
        assertNull(context.steamId)
    }
}
