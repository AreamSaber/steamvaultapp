package com.example.steam_vault_app.domain.security

data class EncryptedVault(
    val version: Int,
    val kdfName: String,
    val cipherName: String,
    val saltBase64: String,
    val nonceBase64: String,
    val ciphertextBase64: String,
)

interface VaultCryptography {
    suspend fun encryptVault(
        cleartextPayload: ByteArray,
        masterKeyMaterial: ByteArray,
    ): EncryptedVault

    suspend fun decryptVault(
        encryptedVault: EncryptedVault,
        masterKeyMaterial: ByteArray,
    ): ByteArray

    fun generateSteamGuardCode(
        sharedSecret: ByteArray,
        timestampSeconds: Long,
    ): String
}
