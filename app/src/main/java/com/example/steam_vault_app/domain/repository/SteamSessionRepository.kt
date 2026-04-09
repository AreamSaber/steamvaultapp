package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamSessionRecord

interface SteamSessionRepository {
    suspend fun getSession(tokenId: String): SteamSessionRecord?

    suspend fun getSessions(): List<SteamSessionRecord>

    suspend fun saveSession(session: SteamSessionRecord)

    suspend fun clearSession(tokenId: String)

    suspend fun clearAllSessions()
}
