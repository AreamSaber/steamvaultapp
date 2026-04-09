package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration

interface CloudBackupRepository {
    suspend fun getConfiguration(): WebDavBackupConfiguration?

    suspend fun saveConfiguration(configuration: WebDavBackupConfiguration)

    suspend fun clearConfiguration()

    suspend fun getStatus(): CloudBackupStatus

    suspend fun saveStatus(status: CloudBackupStatus)
}
