package com.example.steam_vault_app.domain.sync

import com.example.steam_vault_app.domain.model.SyncSnapshot
import com.example.steam_vault_app.domain.model.VaultBlob

interface VaultSyncGateway {
    suspend fun downloadLatestVault(deviceId: String): VaultBlob?

    suspend fun uploadVault(vaultBlob: VaultBlob): SyncSnapshot
}
