package com.example.steam_vault_app.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMasterPasswordManagerTest {
    private lateinit var appContext: Context
    private lateinit var manager: LocalMasterPasswordManager

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        clearPrefs()
        manager = LocalMasterPasswordManager(appContext)
    }

    @After
    fun tearDown() = runBlocking {
        manager.clearUnlockedSession()
        clearPrefs()
    }

    @Test
    fun createAndUnlockWithSamePassword_succeeds() = runBlocking {
        val password = "VaultPass123".toCharArray()

        manager.createMasterPassword(password.copyOf())

        assertNotNull(manager.getActiveVaultKeyMaterial())
        assertTrue(manager.unlock(password.copyOf()))
    }

    @Test
    fun changeMasterPassword_invalidatesOldPasswordAndPreservesVaultKey() = runBlocking {
        val originalPassword = "VaultPass123".toCharArray()
        val nextPassword = "UpdatedPass456".toCharArray()

        manager.createMasterPassword(originalPassword.copyOf())
        val originalVaultKey = manager.getActiveVaultKeyMaterial()
        assertNotNull(originalVaultKey)

        manager.changeMasterPassword(nextPassword.copyOf())

        val updatedVaultKey = manager.getActiveVaultKeyMaterial()
        assertNotNull(updatedVaultKey)
        assertArrayEquals(originalVaultKey, updatedVaultKey)

        manager.clearUnlockedSession()

        assertFalse(manager.unlock(originalPassword.copyOf()))
        assertTrue(manager.unlock(nextPassword.copyOf()))
        assertTrue(
            MessageDigest.isEqual(
                originalVaultKey,
                manager.getActiveVaultKeyMaterial(),
            ),
        )
    }

    @Test
    fun changeMasterPassword_withoutUnlockedSession_throws() = runBlocking {
        val password = "VaultPass123".toCharArray()

        manager.createMasterPassword(password.copyOf())
        manager.clearUnlockedSession()

        var threw = false
        try {
            manager.changeMasterPassword("UpdatedPass456".toCharArray())
        } catch (_: IllegalStateException) {
            threw = true
        }

        assertTrue(threw)
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
    }
}
