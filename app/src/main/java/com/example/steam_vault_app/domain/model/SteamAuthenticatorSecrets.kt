package com.example.steam_vault_app.domain.model

data class SteamAuthenticatorSecrets(
    val sharedSecret: String,
    val identitySecret: String? = null,
    val serialNumber: String? = null,
    val revocationCode: String? = null,
    val secret1: String? = null,
    val deviceId: String? = null,
    val tokenGid: String? = null,
    val uri: String? = null,
    val serverTimeSeconds: Long? = null,
    val fullyEnrolled: Boolean = true,
)
