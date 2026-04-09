package com.example.steam_vault_app.data.security

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.security.EncryptedVault
import com.example.steam_vault_app.domain.security.VaultCryptography
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalVaultCryptography(
    context: Context? = null,
) : VaultCryptography {
    private val secureRandom = SecureRandom()
    private val messages = Messages.fromContext(context)

    override suspend fun encryptVault(
        cleartextPayload: ByteArray,
        masterKeyMaterial: ByteArray,
    ): EncryptedVault = withContext(Dispatchers.Default) {
        validateMasterKeyMaterial(masterKeyMaterial)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(masterKeyMaterial, AES_KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(cleartextPayload)

        EncryptedVault(
            version = CURRENT_VAULT_VERSION,
            kdfName = CURRENT_VAULT_KDF,
            cipherName = CURRENT_VAULT_CIPHER,
            saltBase64 = "",
            nonceBase64 = nonce.toBase64(),
            ciphertextBase64 = ciphertext.toBase64(),
        )
    }

    override suspend fun decryptVault(
        encryptedVault: EncryptedVault,
        masterKeyMaterial: ByteArray,
    ): ByteArray = withContext(Dispatchers.Default) {
        validateMasterKeyMaterial(masterKeyMaterial)
        require(encryptedVault.version == CURRENT_VAULT_VERSION) {
            messages.unsupportedVersion(encryptedVault.version)
        }
        require(encryptedVault.kdfName == CURRENT_VAULT_KDF) {
            messages.unsupportedKdf(encryptedVault.kdfName)
        }
        require(encryptedVault.cipherName == CURRENT_VAULT_CIPHER) {
            messages.unsupportedCipher(encryptedVault.cipherName)
        }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(masterKeyMaterial, AES_KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, encryptedVault.nonceBase64.fromBase64()),
        )
        cipher.doFinal(encryptedVault.ciphertextBase64.fromBase64())
    }

    override fun generateSteamGuardCode(
        sharedSecret: ByteArray,
        timestampSeconds: Long,
    ): String {
        val timeSlice = timestampSeconds / 30L
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(timeSlice)
            .array()

        val hmac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
        hmac.init(SecretKeySpec(sharedSecret, HMAC_SHA1_ALGORITHM))
        val digest = hmac.doFinal(payload)
        val start = digest.last().toInt() and 0x0F
        var codePoint = ((digest[start].toInt() and 0x7F) shl 24) or
            ((digest[start + 1].toInt() and 0xFF) shl 16) or
            ((digest[start + 2].toInt() and 0xFF) shl 8) or
            (digest[start + 3].toInt() and 0xFF)

        val output = StringBuilder(STEAM_CODE_LENGTH)
        repeat(STEAM_CODE_LENGTH) {
            output.append(STEAM_CODE_CHARSET[codePoint % STEAM_CODE_CHARSET.length])
            codePoint /= STEAM_CODE_CHARSET.length
        }
        return output.toString()
    }

    private fun validateMasterKeyMaterial(masterKeyMaterial: ByteArray) {
        require(masterKeyMaterial.size == AES_KEY_BYTES) {
            messages.invalidKeyLength
        }
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.getDecoder().decode(this)
    }

    private data class Messages(
        val unsupportedVersion: (Int) -> String,
        val unsupportedKdf: (String) -> String,
        val unsupportedCipher: (String) -> String,
        val invalidKeyLength: String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        unsupportedVersion = { version -> "Unsupported Vault version: $version" },
                        unsupportedKdf = { kdf -> "Unsupported Vault KDF: $kdf" },
                        unsupportedCipher = { cipher -> "Unsupported Vault cipher: $cipher" },
                        invalidKeyLength = "Vault key must be 32 bytes.",
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    unsupportedVersion = { version ->
                        appContext.getString(R.string.vault_unsupported_version, version)
                    },
                    unsupportedKdf = { kdf ->
                        appContext.getString(R.string.vault_unsupported_kdf, kdf)
                    },
                    unsupportedCipher = { cipher ->
                        appContext.getString(R.string.vault_unsupported_cipher, cipher)
                    },
                    invalidKeyLength = appContext.getString(R.string.vault_key_length_invalid),
                )
            }
        }
    }

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_ALGORITHM = "AES"
        private const val AES_KEY_BYTES = 32
        private const val HMAC_SHA1_ALGORITHM = "HmacSHA1"
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val STEAM_CODE_LENGTH = 5
        private const val STEAM_CODE_CHARSET = "23456789BCDFGHJKMNPQRTVWXY"
        private const val CURRENT_VAULT_VERSION = 1
        private const val CURRENT_VAULT_KDF = "wrapped-vault-key"
        private const val CURRENT_VAULT_CIPHER = "aes-256-gcm"
    }
}
