package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamTimeSyncState

interface SteamTimeRepository {
    suspend fun getState(): SteamTimeSyncState

    suspend fun saveState(state: SteamTimeSyncState)
}
