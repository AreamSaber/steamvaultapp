package com.example.steam_vault_app.data.security

data class KeystoreEncryptedPayload(
    val cipherName: String,
    val nonceBase64: String,
    val ciphertextBase64: String,
)

class AndroidKeystoreVaultKeyProtector {
    private val blobProtector = AndroidKeystoreBlobProtector(KEY_ALIAS)

    fun encrypt(vaultKeyMaterial: ByteArray): KeystoreEncryptedPayload {
        return blobProtector.encrypt(vaultKeyMaterial)
    }

    fun decrypt(payload: KeystoreEncryptedPayload): ByteArray {
        return blobProtector.decrypt(payload)
    }

    companion object {
        private const val KEY_ALIAS = "steam_vault_local_vault_key"
    }
}
