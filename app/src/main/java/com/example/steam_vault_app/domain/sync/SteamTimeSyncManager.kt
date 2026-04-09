package com.example.steam_vault_app.domain.sync

import com.example.steam_vault_app.domain.model.SteamTimeSyncState

interface SteamTimeSyncManager {
    suspend fun syncNow(): SteamTimeSyncState
}
