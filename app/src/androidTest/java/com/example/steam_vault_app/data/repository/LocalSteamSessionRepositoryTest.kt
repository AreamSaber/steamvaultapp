package com.example.steam_vault_app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.LocalMasterPasswordManager
import com.example.steam_vault_app.data.security.LocalVaultCryptography
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSteamSessionRepositoryTest {
    private lateinit var appContext: Context
    private lateinit var masterPasswordManager: LocalMasterPasswordManager
    private lateinit var repository: LocalSteamSessionRepository

    @Before
    fun setUp() = runBlocking {
        appContext = ApplicationProvider.getApplicationContext()
        clearPrefs()
        masterPasswordManager = LocalMasterPasswordManager(appContext)
        masterPasswordManager.createMasterPassword("VaultPass123".toCharArray())
        repository = LocalSteamSessionRepository(
            context = appContext,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = LocalVaultCryptography(),
        )
    }

    @After
    fun tearDown() = runBlocking {
        masterPasswordManager.clearUnlockedSession()
        clearPrefs()
    }

    @Test
    fun saveSession_thenReadAndClear_succeeds() = runBlocking {
        val session = SteamSessionRecord(
            tokenId = "token-1",
            accountName = "demo-account",
            steamId = "76561198000000000",
            sessionId = "session-id",
            cookies = listOf(
                SteamSessionCookie("sessionid", "abc123"),
                SteamSessionCookie("steamLoginSecure", "secure-cookie"),
            ),
            oauthToken = "oauth-token",
            createdAt = "2026-04-07T19:00:00Z",
            updatedAt = "2026-04-07T19:05:00Z",
        )

        repository.saveSession(session)

        assertEquals(session, repository.getSession("token-1"))

        repository.clearSession("token-1")

        assertNull(repository.getSession("token-1"))
    }

    private fun clearPrefs() {
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.SECURITY_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.VAULT_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        appContext.getSharedPreferences(
            SteamVaultPreferenceKeys.STEAM_SESSION_PREFS,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
