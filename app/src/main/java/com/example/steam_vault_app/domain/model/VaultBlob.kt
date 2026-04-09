package com.example.steam_vault_app.domain.model

data class VaultBlob(
    val userId: String,
    val deviceId: String,
    val version: Int,
    val vaultCiphertext: String,
    val vaultNonce: String,
    val vaultKdf: String,
    val updatedAt: String,
)
