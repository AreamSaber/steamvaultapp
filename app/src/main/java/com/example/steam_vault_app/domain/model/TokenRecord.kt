package com.example.steam_vault_app.domain.model

data class TokenRecord(
    val id: String,
    val platform: String = "steam",
    val accountName: String,
    val sharedSecret: String,
    val identitySecret: String? = null,
    val serialNumber: String? = null,
    val revocationCode: String? = null,
    val secret1: String? = null,
    val deviceId: String? = null,
    val tokenGid: String? = null,
    val uri: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)
