package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.repository.CloudBackupRepository

interface BackgroundSyncCloudBackupRepository : CloudBackupRepository {
    suspend fun getBackgroundSyncConfiguration(): WebDavBackupConfiguration?

    suspend fun refreshBackgroundSyncConfigurationSnapshot(): Boolean

    suspend fun clearBackgroundSyncConfigurationSnapshot()
}
