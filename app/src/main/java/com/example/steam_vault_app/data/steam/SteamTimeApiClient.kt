package com.example.steam_vault_app.data.steam

interface SteamTimeApiClient {
    suspend fun queryServerTimeSeconds(): Long
}
