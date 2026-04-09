package com.example.steam_vault_app.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WrappedVaultKeyEnvelopeCodecTest {
    @Test
    fun encodeAndDecode_preservesOptionalBiometricPayload() {
        val encoded = WrappedVaultKeyEnvelopeCodec.encode(
            WrappedVaultKeyEnvelope(
                version = 1,
                passwordWrappedKey = WrappedKeyCopy(
                    cipherName = "aes-256-gcm",
                    nonceBase64 = "bm9uY2U=",
                    ciphertextBase64 = "Y2lwaGVy",
                ),
                keystoreWrappedKey = KeystoreEncryptedPayload(
                    cipherName = "aes-256-gcm",
                    nonceBase64 = "a2V5",
                    ciphertextBase64 = "c3RvcmU=",
                ),
                biometricWrappedKey = KeystoreEncryptedPayload(
                    cipherName = "aes-256-gcm",
                    nonceBase64 = "Ymlv",
                    ciphertextBase64 = "bWV0cmlj",
                ),
            ),
        )

        val decoded = WrappedVaultKeyEnvelopeCodec.decode(encoded)

        assertEquals("Y2lwaGVy", decoded.passwordWrappedKey.ciphertextBase64)
        assertEquals("c3RvcmU=", decoded.keystoreWrappedKey?.ciphertextBase64)
        assertEquals("bWV0cmlj", decoded.biometricWrappedKey?.ciphertextBase64)
    }

    @Test
    fun decode_legacyEnvelopeWithoutBiometricPayload_staysCompatible() {
        val decoded = WrappedVaultKeyEnvelopeCodec.decode(
            """
            {
              "version": 1,
              "password_wrapped_key": {
                "cipher_name": "aes-256-gcm",
                "nonce_base64": "bm9uY2U=",
                "ciphertext_base64": "Y2lwaGVy"
              },
              "keystore_wrapped_key": {
                "cipher_name": "aes-256-gcm",
                "nonce_base64": "a2V5",
                "ciphertext_base64": "c3RvcmU="
              }
            }
            """.trimIndent(),
        )

        assertNotNull(decoded.keystoreWrappedKey)
        assertNull(decoded.biometricWrappedKey)
    }
}
