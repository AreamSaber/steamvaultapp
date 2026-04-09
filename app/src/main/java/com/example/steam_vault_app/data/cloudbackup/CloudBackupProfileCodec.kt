package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import org.json.JSONObject

internal data class CloudBackupProfile(
    val configuration: WebDavBackupConfiguration?,
    val status: CloudBackupStatus,
)

internal object CloudBackupProfileCodec {
    fun encode(profile: CloudBackupProfile): String {
        return JSONObject()
            .put(
                "configuration",
                profile.configuration?.let {
                    JSONObject()
                        .put("server_url", it.serverUrl)
                        .put("username", it.username)
                        .put("app_password", it.appPassword)
                        .put("remote_path", it.remotePath)
                },
            )
            .put(
                "status",
                JSONObject()
                    .put("sync_state", profile.status.syncState.name)
                    .put("account_label", profile.status.accountLabel)
                    .put("remote_path", profile.status.remotePath)
                    .put("last_upload_at", profile.status.lastUploadAt)
                    .put("last_download_at", profile.status.lastDownloadAt)
                    .put("last_error_message", profile.status.lastErrorMessage)
                    .put("auto_backup_state", profile.status.autoBackupState.name)
                    .put("auto_backup_reason", profile.status.autoBackupReason?.name)
                    .put("auto_backup_updated_at", profile.status.autoBackupUpdatedAt)
                    .put("auto_backup_next_run_at", profile.status.autoBackupNextRunAt)
                    .put("auto_backup_failure_count", profile.status.autoBackupFailureCount),
            )
            .toString()
    }

    fun decode(rawPayload: String): CloudBackupProfile {
        val json = JSONObject(rawPayload)
        val configurationJson = json.optJSONObject("configuration")
        val statusJson = json.optJSONObject("status")

        return CloudBackupProfile(
            configuration = configurationJson?.let {
                WebDavBackupConfiguration(
                    serverUrl = it.getString("server_url"),
                    username = it.getString("username"),
                    appPassword = it.getString("app_password"),
                    remotePath = it.getString("remote_path"),
                ).normalized()
            },
            status = CloudBackupStatus(
                syncState = statusJson?.optString("sync_state")
                    ?.takeIf(String::isNotBlank)
                    ?.let(CloudBackupSyncState::valueOf)
                    ?: if (configurationJson == null) {
                        CloudBackupSyncState.NOT_CONFIGURED
                    } else {
                        CloudBackupSyncState.IDLE
                    },
                accountLabel = statusJson?.optString("account_label")?.takeIf(String::isNotBlank),
                remotePath = statusJson?.optString("remote_path")?.takeIf(String::isNotBlank),
                lastUploadAt = statusJson?.optString("last_upload_at")?.takeIf(String::isNotBlank),
                lastDownloadAt = statusJson?.optString("last_download_at")?.takeIf(String::isNotBlank),
                lastErrorMessage = statusJson?.optString("last_error_message")
                    ?.takeIf(String::isNotBlank),
                autoBackupState = statusJson?.optString("auto_backup_state")
                    ?.takeIf(String::isNotBlank)
                    ?.let(CloudBackupAutoBackupState::valueOf)
                    ?: CloudBackupAutoBackupState.IDLE,
                autoBackupReason = statusJson?.optString("auto_backup_reason")
                    ?.takeIf(String::isNotBlank)
                    ?.let(CloudBackupAutoBackupReason::valueOf),
                autoBackupUpdatedAt = statusJson?.optString("auto_backup_updated_at")
                    ?.takeIf(String::isNotBlank),
                autoBackupNextRunAt = statusJson?.optString("auto_backup_next_run_at")
                    ?.takeIf(String::isNotBlank),
                autoBackupFailureCount = statusJson?.optInt("auto_backup_failure_count") ?: 0,
            ),
        )
    }
}
