package com.example.steam_vault_app.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidBiometricVaultKeyProtector {
    fun prepareEncryptionCipher(): Cipher {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return cipher
    }

    fun prepareDecryptionCipher(payload: KeystoreEncryptedPayload): Cipher {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, payload.nonceBase64.fromBase64()),
        )
        return cipher
    }

    fun encryptWithCipher(
        vaultKeyMaterial: ByteArray,
        cipher: Cipher,
    ): KeystoreEncryptedPayload {
        val ciphertext = cipher.doFinal(vaultKeyMaterial)
        return KeystoreEncryptedPayload(
            cipherName = CIPHER_NAME,
            nonceBase64 = cipher.iv.toBase64(),
            ciphertextBase64 = ciphertext.toBase64(),
        )
    }

    fun decryptWithCipher(
        payload: KeystoreEncryptedPayload,
        cipher: Cipher,
    ): ByteArray {
        return cipher.doFinal(payload.ciphertextBase64.fromBase64())
    }

    fun deleteSecretKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
            load(null)
        }
        keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        loadExistingSecretKey()?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER,
        )
        val keySpecBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            keySpecBuilder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }

        keyGenerator.init(keySpecBuilder.build())
        return keyGenerator.generateKey()
    }

    private fun loadExistingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
            load(null)
        }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun ByteArray.toBase64(): String {
        return android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    }

    private fun String.fromBase64(): ByteArray {
        return android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "steam_vault_biometric_vault_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val CIPHER_NAME = "aes-256-gcm"
    }
}
