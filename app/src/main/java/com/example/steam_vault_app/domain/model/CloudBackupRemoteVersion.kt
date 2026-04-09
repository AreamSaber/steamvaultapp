package com.example.steam_vault_app.domain.model

data class CloudBackupRemoteVersion(
    val remotePath: String,
    val fileName: String,
    val uploadedAt: String? = null,
)
