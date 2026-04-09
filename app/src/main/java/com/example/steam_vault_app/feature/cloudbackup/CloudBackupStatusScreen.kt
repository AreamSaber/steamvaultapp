package com.example.steam_vault_app.feature.cloudbackup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.cloud_backup_status_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.cloud_backup_status_plan_title),
                description = stringResource(R.string.cloud_backup_status_plan_description),
            ) {
                ChecklistRow(label = stringResource(R.string.cloud_backup_status_plan_encrypted_only), highlighted = true)
                ChecklistRow(label = stringResource(R.string.cloud_backup_status_plan_manage_local_state), highlighted = true)
                ChecklistRow(label = stringResource(R.string.cloud_backup_status_plan_manual_upload_restore), highlighted = true)
            }
        }
        actionError?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        actionMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        when (val currentState = statusState) {
            AppUiState.Loading -> {
                item {
                    Text(stringResource(R.string.cloud_backup_status_loading))
                }
            }

            is AppUiState.Error -> {
                item {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is AppUiState.Success -> {
                val configuration = currentState.value.configuration
                val status = currentState.value.status
                val remoteVersions = currentState.value.remoteVersions

                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_backup_status_configuration_title),
                        description = syncStateDescription(context, status.syncState),
                    ) {
                        ChecklistRow(
                            label = if (configuration == null) {
                                stringResource(R.string.cloud_backup_status_not_configured)
                            } else {
                                stringResource(R.string.cloud_backup_status_configured)
                            },
                            highlighted = configuration != null,
                        )
                        configuration?.let {
                            Text(
                                text = context.getString(R.string.cloud_backup_status_service_url, it.serverUrl),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = context.getString(R.string.cloud_backup_status_account, it.username),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = context.getString(R.string.cloud_backup_status_remote_path, it.remotePath),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_backup_status_records_title),
                        description = stringResource(R.string.cloud_backup_status_records_description),
                    ) {
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_last_upload,
                                status.lastUploadAt ?: context.getString(R.string.cloud_backup_status_last_upload_none),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_last_download,
                                status.lastDownloadAt ?: context.getString(R.string.cloud_backup_status_last_download_none),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!status.lastErrorMessage.isNullOrBlank()) {
                            Text(
                                text = context.getString(
                                    R.string.cloud_backup_status_last_error,
                                    status.lastErrorMessage,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_backup_status_auto_title),
                        description = stringResource(R.string.cloud_backup_status_auto_description),
                    ) {
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_auto_state,
                                automaticBackupStateDescription(context, status.autoBackupState),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_auto_reason,
                                automaticBackupReasonDescription(context, status.autoBackupReason),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_auto_updated_at,
                                status.autoBackupUpdatedAt
                                    ?: context.getString(R.string.cloud_backup_status_auto_updated_at_none),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_auto_next_run_at,
                                status.autoBackupNextRunAt
                                    ?: context.getString(R.string.cloud_backup_status_auto_next_run_at_none),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = context.getString(
                                R.string.cloud_backup_status_auto_failure_count,
                                status.autoBackupFailureCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.cloud_backup_status_versions_title),
                        description = stringResource(R.string.cloud_backup_status_versions_description),
                    ) {
                        if (remoteVersions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.cloud_backup_status_versions_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.cloud_backup_status_versions_count,
                                    remoteVersions.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            remoteVersions.forEach { version ->
                                Text(
                                    text = context.getString(
                                        R.string.cloud_backup_status_version_label,
                                        version.uploadedAt
                                            ?: context.getString(R.string.cloud_backup_status_version_uploaded_at_unknown),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = context.getString(
                                        R.string.cloud_backup_status_version_path,
                                        version.remotePath,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isWorking = true
                                            actionError = null
                                            actionMessage = null

                                            try {
                                                cloudBackupSyncManager.restoreBackup(version)
                                                actionMessage = context.getString(
                                                    R.string.cloud_backup_status_restore_version_success,
                                                    version.uploadedAt
                                                        ?: context.getString(
                                                            R.string.cloud_backup_status_version_uploaded_at_unknown,
                                                        ),
                                                )
                                                refreshVersion += 1
                                                onCloudBackupRestored()
                                            } catch (error: Exception) {
                                                actionError = error.message
                                                    ?: context.getString(
                                                        R.string.cloud_backup_status_restore_version_failed,
                                                    )
                                            } finally {
                                                isWorking = false
                                            }
                                        }
                                    },
                                    enabled = !isWorking,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.cloud_backup_status_restore_version_action))
                                }
                            }
                        }
                    }
                }
            }

            AppUiState.Empty -> Unit
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isWorking) {
                                R.string.cloud_backup_status_upload_action_loading
                            } else {
                                R.string.cloud_backup_status_upload_action_idle
                            },
                        ),
                    )
                }
                OutlinedButton(
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cloud_backup_status_restore_action))
                }
                OutlinedButton(
                    onClick = onOpenConfiguration,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cloud_backup_status_edit_config))
                }
                OutlinedButton(
                    onClick = onOpenLocalBackupExport,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cloud_backup_status_open_local_export))
                }
                OutlinedButton(
                    onClick = onOpenLocalBackupRestore,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cloud_backup_status_open_local_restore))
                }
            }
        }
    }
}

private data class CloudBackupStatusPageState(
    val configuration: WebDavBackupConfiguration?,
    val status: CloudBackupStatus,
    val remoteVersions: List<CloudBackupRemoteVersion>,
)

private fun automaticBackupStateDescription(
    context: android.content.Context,
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
    context: android.content.Context,
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
    context: android.content.Context,
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
