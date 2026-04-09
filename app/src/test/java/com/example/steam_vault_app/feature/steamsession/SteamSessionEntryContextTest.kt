package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamSessionEntryContextTest {
    @Test
    fun importedExistingAuthenticator_trimsSuggestedValues() {
        val context = SteamSessionEntryContext.importedExistingAuthenticator(
            steamId = " 76561198000000003 ",
            accountName = " demo-account ",
        )

        assertEquals(
            SteamSessionEntryContext.Kind.IMPORTED_EXISTING_AUTHENTICATOR,
            context.kind,
        )
        assertEquals("76561198000000003", context.suggestedSteamId)
        assertEquals("demo-account", context.suggestedAccountName)
    }

    @Test
    fun store_consumesOnlyMatchingToken() {
        val entryContext = SteamSessionEntryContext.importedExistingAuthenticator(
            steamId = "76561198000000004",
            accountName = "demo",
        )
        SteamSessionEntryContextStore.push(
            tokenId = "token-a",
            entryContext = entryContext,
        )

        assertNull(SteamSessionEntryContextStore.consume("token-b"))
        assertEquals(entryContext, SteamSessionEntryContextStore.consume("token-a"))
        assertNull(SteamSessionEntryContextStore.consume("token-a"))
    }
}
