package com.example.steam_vault_app.domain.security

data class MasterPasswordBackupSnapshot(
    val masterPasswordSaltBase64: String,
    val masterPasswordHashBase64: String,
    val masterPasswordKdfName: String = "pbkdf2",
    val masterPasswordIterations: Int,
    val masterPasswordMemoryKiB: Int = 0,
    val masterPasswordParallelism: Int = 1,
    val masterPasswordVersion: Int,
    val vaultKeySaltBase64: String,
    val wrappedVaultKeyJson: String,
)
