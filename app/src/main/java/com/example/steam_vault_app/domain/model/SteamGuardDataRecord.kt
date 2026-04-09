package com.example.steam_vault_app.domain.model

data class SteamGuardDataRecord(
    val accountName: String? = null,
    val steamId: String? = null,
    val guardData: String,
    val updatedAt: String,
) {
    init {
        require(!accountName.isNullOrBlank() || !steamId.isNullOrBlank()) {
            "SteamGuardDataRecord requires an accountName or steamId."
        }
        require(guardData.isNotBlank()) {
            "SteamGuardDataRecord.guardData must not be blank."
        }
    }
}
