package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import org.json.JSONObject

internal object CloudBackupStatusCodec {
    fun encode(status: CloudBackupStatus): String {
        return JSONObject()
            .put("sync_state", status.syncState.name)
            .put("account_label", status.accountLabel)
            .put("remote_path", status.remotePath)
            .put("last_upload_at", status.lastUploadAt)
            .put("last_download_at", status.lastDownloadAt)
            .put("last_error_message", status.lastErrorMessage)
            .put("auto_backup_state", status.autoBackupState.name)
            .put("auto_backup_reason", status.autoBackupReason?.name)
            .put("auto_backup_updated_at", status.autoBackupUpdatedAt)
            .put("auto_backup_next_run_at", status.autoBackupNextRunAt)
            .put("auto_backup_failure_count", status.autoBackupFailureCount)
            .toString()
    }

    fun decode(rawPayload: String): CloudBackupStatus {
        val json = JSONObject(rawPayload)
        return CloudBackupStatus(
            syncState = json.optString("sync_state")
                .takeIf(String::isNotBlank)
                ?.let(CloudBackupSyncState::valueOf)
                ?: CloudBackupSyncState.IDLE,
            accountLabel = json.optString("account_label").takeIf(String::isNotBlank),
            remotePath = json.optString("remote_path").takeIf(String::isNotBlank),
            lastUploadAt = json.optString("last_upload_at").takeIf(String::isNotBlank),
            lastDownloadAt = json.optString("last_download_at").takeIf(String::isNotBlank),
            lastErrorMessage = json.optString("last_error_message").takeIf(String::isNotBlank),
            autoBackupState = json.optString("auto_backup_state")
                .takeIf(String::isNotBlank)
                ?.let(CloudBackupAutoBackupState::valueOf)
                ?: CloudBackupAutoBackupState.IDLE,
            autoBackupReason = json.optString("auto_backup_reason")
                .takeIf(String::isNotBlank)
                ?.let(CloudBackupAutoBackupReason::valueOf),
            autoBackupUpdatedAt = json.optString("auto_backup_updated_at").takeIf(String::isNotBlank),
            autoBackupNextRunAt = json.optString("auto_backup_next_run_at").takeIf(String::isNotBlank),
            autoBackupFailureCount = json.optInt("auto_backup_failure_count"),
        )
    }
}
