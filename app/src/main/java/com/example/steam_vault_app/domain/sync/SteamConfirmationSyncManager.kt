package com.example.steam_vault_app.domain.sync

import com.example.steam_vault_app.domain.model.SteamConfirmation

interface SteamConfirmationSyncManager {
    suspend fun fetchConfirmations(tokenId: String): List<SteamConfirmation>

    suspend fun approveConfirmation(
        tokenId: String,
        confirmationId: String,
        confirmationNonce: String,
    ): List<SteamConfirmation>

    suspend fun rejectConfirmation(
        tokenId: String,
        confirmationId: String,
        confirmationNonce: String,
    ): List<SteamConfirmation>
}
