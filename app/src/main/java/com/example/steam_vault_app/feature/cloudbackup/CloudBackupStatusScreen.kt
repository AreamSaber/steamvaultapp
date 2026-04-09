package com.example.steam_vault_app.feature.cloudbackup

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import kotlinx.coroutines.launch

@Composable
fun CloudBackupStatusScreen(
    cloudBackupRepository: CloudBackupRepository,
    cloudBackupSyncManager: CloudBackupSyncManager,
    onOpenConfiguration: () -> Unit,
    onOpenLocalBackupExport: () -> Unit,
    onOpenLocalBackupRestore: () -> Unit,
    onCloudBackupRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var actionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }

    val statusState by produceState<AppUiState<CloudBackupStatusPageState>>(
        initialValue = AppUiState.Loading,
        key1 = cloudBackupRepository,
        key2 = cloudBackupSyncManager,
        key3 = refreshVersion,
    ) {
        value = try {
            val configuration = cloudBackupRepository.getConfiguration()
            val status = cloudBackupRepository.getStatus()
            val versions = if (configuration == null) {
                emptyList()
            } else {
                cloudBackupSyncManager.listAvailableBackups()
            }
            AppUiState.Success(
                CloudBackupStatusPageState(
                    configuration = configuration,
                    status = status,
                    remoteVersions = versions,
                ),
            )
        } catch (_: Exception) {
            AppUiState.Error(context.getString(R.string.cloud_backup_status_load_failed))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.cloud_status_modern_title),
                subtitle = stringResource(R.string.cloud_status_modern_body),
            )
        }
        actionError?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Error,
                )
            }
        }
        actionMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Success,
                )
            }
        }
        when (val currentState = statusState) {
            AppUiState.Loading -> item {
                VaultInlineBanner(
                    text = stringResource(R.string.cloud_backup_status_loading),
                    tone = VaultBannerTone.Neutral,
                )
            }

            is AppUiState.Error -> item {
                VaultInlineBanner(
                    text = currentState.message,
                    tone = VaultBannerTone.Error,
                )
            }

            is AppUiState.Success -> {
                val configuration = currentState.value.configuration
                val status = currentState.value.status
                val remoteVersions = currentState.value.remoteVersions

                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_connection_title),
                        description = if (configuration == null) {
                            stringResource(R.string.cloud_status_modern_connection_missing)
                        } else {
                            stringResource(R.string.cloud_status_modern_connection_ready)
                        },
                    ) {
                        configuration?.let {
                            StatusValueLine(
                                label = stringResource(R.string.cloud_config_modern_url),
                                value = it.serverUrl,
                            )
                            StatusValueLine(
                                label = stringResource(R.string.cloud_config_modern_username),
                                value = it.username,
                            )
                            StatusValueLine(
                                label = stringResource(R.string.cloud_config_modern_path),
                                value = it.remotePath,
                            )
                        }
                        VaultSecondaryButton(
                            text = stringResource(R.string.cloud_status_modern_edit_connection),
                            onClick = onOpenConfiguration,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_storage_title),
                        description = stringResource(R.string.cloud_status_modern_storage_body),
                    ) {
                        StatusValueLine(
                            label = stringResource(R.string.cloud_status_modern_last_sync_label),
                            value = status.lastUploadAt ?: stringResource(R.string.cloud_backup_status_last_upload_none),
                        )
                        StatusValueLine(
                            label = stringResource(R.string.cloud_status_modern_versions_title),
                            value = remoteVersions.size.toString(),
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_recent_title),
                        description = stringResource(R.string.cloud_status_modern_recent_body),
                    ) {
                        StatusValueLine(
                            label = stringResource(R.string.cloud_status_modern_recent_upload_label),
                            value = status.lastUploadAt ?: stringResource(R.string.cloud_backup_status_last_upload_none),
                        )
                        StatusValueLine(
                            label = stringResource(R.string.cloud_status_modern_recent_restore_label),
                            value = status.lastDownloadAt ?: stringResource(R.string.cloud_backup_status_last_download_none),
                        )
                        status.lastErrorMessage?.let { message ->
                            VaultInlineBanner(
                                text = message,
                                tone = VaultBannerTone.Warning,
                            )
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_auto_title),
                        description = stringResource(R.string.cloud_status_modern_auto_body),
                    ) {
                        StatusValueLine(
                            label = stringResource(R.string.cloud_backup_status_auto_state_label),
                            value = automaticBackupStateDescription(context, status.autoBackupState),
                        )
                        StatusValueLine(
                            label = stringResource(R.string.cloud_backup_status_auto_reason_label),
                            value = automaticBackupReasonDescription(context, status.autoBackupReason),
                        )
                        StatusValueLine(
                            label = stringResource(R.string.cloud_backup_status_auto_next_run_label),
                            value = status.autoBackupNextRunAt
                                ?: stringResource(R.string.cloud_backup_status_auto_next_run_at_none),
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_versions_title),
                        description = if (remoteVersions.isEmpty()) {
                            stringResource(R.string.cloud_status_modern_versions_empty)
                        } else {
                            stringResource(R.string.cloud_status_modern_manage_history)
                        },
                    ) {
                        if (remoteVersions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.cloud_status_modern_versions_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            remoteVersions.forEachIndexed { index, version ->
                                CloudVersionCard(
                                    version = version,
                                    latest = index == 0,
                                    isWorking = isWorking,
                                    onRestore = {
                                        scope.launch {
                                            isWorking = true
                                            actionError = null
                                            actionMessage = null
                                            try {
                                                cloudBackupSyncManager.restoreBackup(version)
                                                actionMessage = context.getString(
                                                    R.string.cloud_backup_status_restore_version_success,
                                                    version.uploadedAt
                                                        ?: context.getString(R.string.cloud_backup_status_version_uploaded_at_unknown),
                                                )
                                                refreshVersion += 1
                                                onCloudBackupRestored()
                                            } catch (error: Exception) {
                                                actionError = error.message
                                                    ?: context.getString(R.string.cloud_backup_status_restore_version_failed)
                                            } finally {
                                                isWorking = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_tools_title),
                        description = syncStateDescription(context, status.syncState),
                    ) {
                        VaultPrimaryButton(
                            text = stringResource(R.string.cloud_status_modern_upload_action),
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    actionError = null
                                    actionMessage = null
                                    try {
                                        cloudBackupSyncManager.uploadCurrentBackup()
                                        actionMessage = context.getString(R.string.cloud_backup_status_upload_success)
                                        refreshVersion += 1
                                    } catch (error: Exception) {
                                        actionError = error.message ?: context.getString(R.string.cloud_backup_status_upload_failed)
                                    } finally {
                                        isWorking = false
                                    }
                                }
                            },
                            enabled = !isWorking,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.cloud_status_modern_restore_latest_action),
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    actionError = null
                                    actionMessage = null
                                    try {
                                        cloudBackupSyncManager.restoreLatestBackup()
                                        actionMessage = context.getString(R.string.cloud_backup_status_restore_success)
                                        refreshVersion += 1
                                        onCloudBackupRestored()
                                    } catch (error: Exception) {
                                        actionError = error.message ?: context.getString(R.string.cloud_backup_status_restore_failed)
                                    } finally {
                                        isWorking = false
                                    }
                                }
                            },
                            enabled = !isWorking,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.cloud_status_modern_local_export),
                            onClick = onOpenLocalBackupExport,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.cloud_status_modern_local_restore),
                            onClick = onOpenLocalBackupRestore,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_status_modern_security_title),
                        description = stringResource(R.string.cloud_status_modern_security_body),
                    ) {}
                }
            }

            AppUiState.Empty -> Unit
        }
    }
}

private data class CloudBackupStatusPageState(
    val configuration: WebDavBackupConfiguration?,
    val status: CloudBackupStatus,
    val remoteVersions: List<CloudBackupRemoteVersion>,
)

@Composable
private fun StatusValueLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CloudVersionCard(
    version: CloudBackupRemoteVersion,
    latest: Boolean,
    isWorking: Boolean,
    onRestore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = version.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = version.uploadedAt
                            ?: stringResource(R.string.cloud_backup_status_version_uploaded_at_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (latest) {
                    Text(
                        text = stringResource(R.string.cloud_status_modern_versions_latest),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = version.remotePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VaultSecondaryButton(
                text = stringResource(R.string.cloud_backup_status_restore_version_action),
                onClick = onRestore,
                enabled = !isWorking,
            )
        }
    }
}

private fun automaticBackupStateDescription(
    context: Context,
    autoBackupState: CloudBackupAutoBackupState,
): String {
    return when (autoBackupState) {
        CloudBackupAutoBackupState.IDLE -> context.getString(R.string.cloud_backup_auto_state_idle)
        CloudBackupAutoBackupState.SCHEDULED -> context.getString(R.string.cloud_backup_auto_state_scheduled)
        CloudBackupAutoBackupState.RETRY_SCHEDULED -> context.getString(R.string.cloud_backup_auto_state_retry_scheduled)
        CloudBackupAutoBackupState.SUCCESS -> context.getString(R.string.cloud_backup_auto_state_success)
        CloudBackupAutoBackupState.ERROR -> context.getString(R.string.cloud_backup_auto_state_error)
        CloudBackupAutoBackupState.PAUSED_AFTER_RESTORE -> {
            context.getString(R.string.cloud_backup_auto_state_paused_after_restore)
        }
    }
}

private fun automaticBackupReasonDescription(
    context: Context,
    autoBackupReason: CloudBackupAutoBackupReason?,
): String {
    return when (autoBackupReason) {
        CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED -> {
            context.getString(R.string.cloud_backup_auto_reason_vault_content_changed)
        }
        CloudBackupAutoBackupReason.SECURITY_SETTINGS_CHANGED -> {
            context.getString(R.string.cloud_backup_auto_reason_security_settings_changed)
        }
        CloudBackupAutoBackupReason.CONFIGURATION_CHANGED -> {
            context.getString(R.string.cloud_backup_auto_reason_configuration_changed)
        }
        CloudBackupAutoBackupReason.MASTER_PASSWORD_CHANGED -> {
            context.getString(R.string.cloud_backup_auto_reason_master_password_changed)
        }
        CloudBackupAutoBackupReason.MANUAL_RESTORE -> {
            context.getString(R.string.cloud_backup_auto_reason_manual_restore)
        }
        null -> context.getString(R.string.cloud_backup_status_auto_reason_none)
    }
}

private fun syncStateDescription(
    context: Context,
    syncState: CloudBackupSyncState,
): String {
    return when (syncState) {
        CloudBackupSyncState.NOT_CONFIGURED -> context.getString(R.string.cloud_backup_sync_state_not_configured)
        CloudBackupSyncState.IDLE -> context.getString(R.string.cloud_backup_sync_state_idle)
        CloudBackupSyncState.UPLOADING -> context.getString(R.string.cloud_backup_sync_state_uploading)
        CloudBackupSyncState.DOWNLOADING -> context.getString(R.string.cloud_backup_sync_state_downloading)
        CloudBackupSyncState.SUCCESS -> context.getString(R.string.cloud_backup_sync_state_success)
        CloudBackupSyncState.ERROR -> context.getString(R.string.cloud_backup_sync_state_error)
    }
}
