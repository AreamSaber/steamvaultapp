package com.example.steam_vault_app.domain.model

data class WebDavBackupConfiguration(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val remotePath: String,
) {
    fun normalized(): WebDavBackupConfiguration {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        val normalizedRemotePath = remotePath.trim().let { path ->
            val withoutDuplicateSlash = path.replace("/+".toRegex(), "/")
            when {
                withoutDuplicateSlash.isBlank() -> DEFAULT_REMOTE_PATH
                withoutDuplicateSlash.startsWith("/") -> withoutDuplicateSlash
                else -> "/$withoutDuplicateSlash"
            }
        }

        return copy(
            serverUrl = normalizedServerUrl,
            username = username.trim(),
            appPassword = appPassword.trim(),
            remotePath = normalizedRemotePath,
        )
    }

    companion object {
        const val DEFAULT_REMOTE_PATH = "/SteamVault/steam-vault-backup.json"
    }
}
