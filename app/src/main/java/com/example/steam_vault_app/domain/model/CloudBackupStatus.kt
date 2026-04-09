package com.example.steam_vault_app.domain.model

enum class CloudBackupSyncState {
    NOT_CONFIGURED,
    IDLE,
    UPLOADING,
    DOWNLOADING,
    SUCCESS,
    ERROR,
}

enum class CloudBackupAutoBackupState {
    IDLE,
    SCHEDULED,
    RETRY_SCHEDULED,
    SUCCESS,
    ERROR,
    PAUSED_AFTER_RESTORE,
}

enum class CloudBackupAutoBackupReason {
    VAULT_CONTENT_CHANGED,
    SECURITY_SETTINGS_CHANGED,
    CONFIGURATION_CHANGED,
    MASTER_PASSWORD_CHANGED,
    MANUAL_RESTORE,
}

data class CloudBackupStatus(
    val syncState: CloudBackupSyncState = CloudBackupSyncState.NOT_CONFIGURED,
    val accountLabel: String? = null,
    val remotePath: String? = null,
    val lastUploadAt: String? = null,
    val lastDownloadAt: String? = null,
    val lastErrorMessage: String? = null,
    val autoBackupState: CloudBackupAutoBackupState = CloudBackupAutoBackupState.IDLE,
    val autoBackupReason: CloudBackupAutoBackupReason? = null,
    val autoBackupUpdatedAt: String? = null,
    val autoBackupNextRunAt: String? = null,
    val autoBackupFailureCount: Int = 0,
)
