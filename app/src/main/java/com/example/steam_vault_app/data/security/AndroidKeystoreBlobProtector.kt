package com.example.steam_vault_app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreBlobProtector(
    private val keyAlias: String,
) {
    fun encrypt(cleartext: ByteArray): KeystoreEncryptedPayload {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(cleartext)

        return KeystoreEncryptedPayload(
            cipherName = CIPHER_NAME,
            nonceBase64 = cipher.iv.toBase64(),
            ciphertextBase64 = ciphertext.toBase64(),
        )
    }

    fun decrypt(payload: KeystoreEncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, payload.nonceBase64.fromBase64()),
        )
        return cipher.doFinal(payload.ciphertextBase64.fromBase64())
    }

    private fun getOrCreateSecretKey(): SecretKey {
        loadExistingSecretKey()?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER,
        )
        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun loadExistingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
            load(null)
        }
        return keyStore.getKey(keyAlias, null) as? SecretKey
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val CIPHER_NAME = "aes-256-gcm"
    }
}

private fun ByteArray.toBase64(): String {
    return Base64.encodeToString(this, Base64.NO_WRAP)
}

private fun String.fromBase64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}
