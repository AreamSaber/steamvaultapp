package com.example.steam_vault_app.feature.importtoken

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTokenScaffoldFactoryTest {
    @Test
    fun buildExistingAuthenticatorJson_includesSuggestedContextAndPlaceholders() {
        val scaffold = ImportTokenScaffoldFactory.buildExistingAuthenticatorJson(
            ImportTokenEntryContext.existingAuthenticator(
                accountName = "demo-account",
                steamId = "76561198000000001",
                deviceId = "android:demo-device-id",
            ),
        ) ?: throw AssertionError("Expected scaffold to be created")

        assertTrue(scaffold.contains("\"account_name\": \"demo-account\""))
        assertTrue(scaffold.contains("\"steamid\": \"76561198000000001\""))
        assertTrue(scaffold.contains("\"device_id\": \"android:demo-device-id\""))
        assertTrue(scaffold.contains("\"shared_secret\": \"PASTE_SHARED_SECRET_HERE\""))
        assertTrue(scaffold.contains("\"identity_secret\": \"PASTE_IDENTITY_SECRET_HERE\""))
    }

    @Test
    fun buildExistingAuthenticatorJson_omitsOptionalFieldsWhenUnavailable() {
        val scaffold = ImportTokenScaffoldFactory.buildExistingAuthenticatorJson(
            ImportTokenEntryContext.existingAuthenticator(
                accountName = null,
                steamId = "76561198000000002",
                deviceId = null,
            ),
        ) ?: throw AssertionError("Expected scaffold to be created")

        assertTrue(scaffold.contains("\"account_name\": \"Steam 76561198000000002\""))
        assertTrue(scaffold.contains("\"steamid\": \"76561198000000002\""))
        assertFalse(scaffold.contains("\"device_id\""))
    }
}
