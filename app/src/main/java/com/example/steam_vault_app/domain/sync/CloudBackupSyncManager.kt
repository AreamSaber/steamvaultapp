package com.example.steam_vault_app.domain.sync

import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration

interface CloudBackupSyncManager {
    suspend fun uploadCurrentBackup()

    suspend fun uploadCurrentBackup(configuration: WebDavBackupConfiguration) {
        uploadCurrentBackup()
    }

    suspend fun restoreLatestBackup()

    suspend fun listAvailableBackups(): List<CloudBackupRemoteVersion>

    suspend fun restoreBackup(version: CloudBackupRemoteVersion)
}
