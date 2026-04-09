package com.example.steam_vault_app.domain.sync

import com.example.steam_vault_app.domain.model.SteamSessionRecord

interface SteamSessionValidationSyncManager {
    suspend fun validateSession(tokenId: String): SteamSessionRecord
}
