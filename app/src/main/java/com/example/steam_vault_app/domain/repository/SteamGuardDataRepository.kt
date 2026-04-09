package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamGuardDataRecord

interface SteamGuardDataRepository {
    suspend fun getGuardData(
        accountName: String? = null,
        steamId: String? = null,
    ): String?

    suspend fun saveGuardData(record: SteamGuardDataRecord)

    suspend fun clearGuardData(
        accountName: String? = null,
        steamId: String? = null,
    )
}
