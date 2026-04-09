package com.example.steam_vault_app.domain.model

data class SteamConfirmation(
    val id: String,
    val nonce: String,
    val typeCode: Int? = null,
    val headline: String,
    val summary: List<String> = emptyList(),
    val warn: String? = null,
    val creatorId: String? = null,
    val creationTimeEpochSeconds: Long? = null,
    val isMulti: Boolean = false,
    val iconUrl: String? = null,
)
