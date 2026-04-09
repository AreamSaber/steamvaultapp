package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration

data class WebDavFileEntry(
    val remotePath: String,
    val isDirectory: Boolean,
    val lastModifiedAt: String? = null,
)

interface WebDavClient {
    suspend fun uploadText(
        configuration: WebDavBackupConfiguration,
        payload: String,
        remotePath: String = configuration.remotePath,
    )

    suspend fun downloadText(
        configuration: WebDavBackupConfiguration,
        remotePath: String = configuration.remotePath,
    ): String

    suspend fun listFiles(
        configuration: WebDavBackupConfiguration,
        remoteDirectoryPath: String,
    ): List<WebDavFileEntry>

    suspend fun delete(
        configuration: WebDavBackupConfiguration,
        remotePath: String,
    )
}
