package com.example.steam_vault_app.domain.model

data class SteamQrLoginAuthSessionInfo(
    val challengeUrl: String,
    val clientId: ULong,
    val version: Int,
    val ip: String? = null,
    val geoloc: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val platformType: Int? = null,
    val deviceFriendlyName: String? = null,
    val loginHistory: Int? = null,
    val requestorLocationMismatch: Boolean = false,
    val highUsageLogin: Boolean = false,
    val requestedPersistence: Int? = null,
    val deviceTrust: Int? = null,
    val appType: Int? = null,
)
