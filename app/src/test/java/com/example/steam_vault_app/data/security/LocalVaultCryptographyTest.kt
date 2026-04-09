package com.example.steam_vault_app.data.security
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVaultCryptographyTest {
    private val cryptography = LocalVaultCryptography()
    private val vaultKey = ByteArray(32) { index -> (index + 1).toByte() }

    @Test
    fun encryptThenDecrypt_roundTripsVaultPayload() {
        runBlocking {
            val cleartext = """{"tokens":[{"account_name":"demo"}]}"""
                .toByteArray(StandardCharsets.UTF_8)

            val encrypted = cryptography.encryptVault(cleartext, vaultKey)
            val decrypted = cryptography.decryptVault(encrypted, vaultKey)

            assertEquals(1, encrypted.version)
            assertEquals("wrapped-vault-key", encrypted.kdfName)
            assertEquals("aes-256-gcm", encrypted.cipherName)
            assertEquals("", encrypted.saltBase64)
            assertTrue(encrypted.nonceBase64.isNotBlank())
            assertNotEquals(String(cleartext, StandardCharsets.UTF_8), encrypted.ciphertextBase64)
            assertArrayEquals(cleartext, decrypted)
        }
    }

    @Test
    fun decryptVault_rejectsTamperedCiphertext() {
        runBlocking {
            val encrypted = cryptography.encryptVault(
                cleartextPayload = "sensitive-payload".toByteArray(StandardCharsets.UTF_8),
                masterKeyMaterial = vaultKey,
            )
            val tamperedCiphertext = Base64.getDecoder().decode(encrypted.ciphertextBase64).also {
                it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte()
            }
            val tamperedVault = encrypted.copy(
                ciphertextBase64 = Base64.getEncoder().encodeToString(tamperedCiphertext),
            )

            org.junit.Assert.assertThrows(AEADBadTagException::class.java) {
                runBlocking {
                    cryptography.decryptVault(tamperedVault, vaultKey)
                }
            }
        }
    }

    @Test
    fun decryptVault_rejectsUnexpectedMetadata() {
        runBlocking {
            val encrypted = cryptography.encryptVault(
                cleartextPayload = "payload".toByteArray(StandardCharsets.UTF_8),
                masterKeyMaterial = vaultKey,
            )

            val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    cryptography.decryptVault(
                        encrypted.copy(cipherName = "xchacha20-poly1305"),
                        vaultKey,
                    )
                }
            }

            assertTrue(error.message.orEmpty().contains("Vault"))
        }
    }

    @Test
    fun generateSteamGuardCode_matchesKnownVector() {
        val sharedSecret = Base64.getDecoder().decode("c3RlYW0tc2VjcmV0LWtleQ==")

        val codeAtStart = cryptography.generateSteamGuardCode(sharedSecret, 1_712_448_000L)
        val codeWithinSameSlice = cryptography.generateSteamGuardCode(sharedSecret, 1_712_448_015L)
        val codeAtNextSlice = cryptography.generateSteamGuardCode(sharedSecret, 1_712_448_030L)

        assertEquals("PVWV6", codeAtStart)
        assertEquals("PVWV6", codeWithinSameSlice)
        assertEquals("57F4F", codeAtNextSlice)
    }
}
