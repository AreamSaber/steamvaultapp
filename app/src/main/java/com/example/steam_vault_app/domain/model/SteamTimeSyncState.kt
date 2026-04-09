package com.example.steam_vault_app.domain.model

data class SteamTimeSyncState(
    val status: SteamTimeSyncStatus = SteamTimeSyncStatus.IDLE,
    val offsetSeconds: Long = 0L,
    val lastSyncAt: String? = null,
    val lastServerTimeSeconds: Long? = null,
    val lastErrorMessage: String? = null,
)

enum class SteamTimeSyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR,
}
