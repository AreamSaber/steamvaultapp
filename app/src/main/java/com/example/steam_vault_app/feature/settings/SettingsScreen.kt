package com.example.steam_vault_app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.ui.common.ScreenSectionCard

@Composable
fun SettingsScreen(
    securitySettings: AppSecuritySettings,
    onSecuritySettingsChanged: (AppSecuritySettings) -> Unit,
    onBiometricQuickUnlockToggle: (Boolean) -> Unit,
    biometricQuickUnlockAvailable: Boolean,
    biometricStatusMessage: String?,
    securityStatusMessage: String?,
    onOpenChangePassword: () -> Unit,
    onOpenBackupExport: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenCloudBackupStatus: () -> Unit,
    onOpenCloudBackupConfig: () -> Unit,
    steamTimeSyncState: SteamTimeSyncState,
    isSyncingSteamTime: Boolean,
    onSyncSteamTime: () -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_security_policy_title),
                description = stringResource(R.string.settings_security_policy_description),
            ) {
                SettingToggleRow(
                    label = stringResource(R.string.settings_toggle_secure_screens),
                    checked = securitySettings.secureScreensEnabled,
                    onCheckedChange = { enabled ->
                        onSecuritySettingsChanged(
                            securitySettings.copy(secureScreensEnabled = enabled),
                        )
                    },
                )
                SettingToggleRow(
                    label = stringResource(R.string.settings_toggle_biometric_quick_unlock),
                    checked = securitySettings.biometricQuickUnlockEnabled,
                    onCheckedChange = onBiometricQuickUnlockToggle,
                    enabled = biometricQuickUnlockAvailable || securitySettings.biometricQuickUnlockEnabled,
                )
                Text(
                    text = biometricStatusMessage
                        ?: stringResource(R.string.settings_biometric_default_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (biometricQuickUnlockAvailable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                OutlinedButton(
                    onClick = onOpenChangePassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_change_password_action))
                }
                Text(
                    text = stringResource(R.string.settings_change_password_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                securityStatusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_auto_lock_title),
                description = stringResource(R.string.settings_auto_lock_description),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_auto_lock_current_policy,
                        autoLockLabel(context, securitySettings.autoLockTimeout),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AutoLockTimeoutOption.entries.forEach { option ->
                    val isSelected = securitySettings.autoLockTimeout == option
                    if (isSelected) {
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(autoLockLabel(context, option))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onSecuritySettingsChanged(
                                    securitySettings.copy(autoLockTimeout = option),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(autoLockLabel(context, option))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_auto_lock_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_steam_time_title),
                description = stringResource(R.string.settings_steam_time_description),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_steam_time_current_offset,
                        formatOffsetSeconds(context, steamTimeSyncState.offsetSeconds),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.settings_steam_time_last_sync,
                        steamTimeSyncState.lastSyncAt
                            ?: stringResource(R.string.settings_steam_time_last_sync_none),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (steamTimeSyncState.status) {
                    SteamTimeSyncStatus.ERROR -> {
                        steamTimeSyncState.lastErrorMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    SteamTimeSyncStatus.SUCCESS -> {
                        Text(
                            text = stringResource(R.string.settings_steam_time_success),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SteamTimeSyncStatus.SYNCING -> {
                        Text(
                            text = stringResource(R.string.settings_steam_time_syncing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SteamTimeSyncStatus.IDLE -> {
                        Text(
                            text = stringResource(R.string.settings_steam_time_idle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onSyncSteamTime,
                    enabled = !isSyncingSteamTime,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isSyncingSteamTime) {
                                R.string.settings_steam_time_action_loading
                            } else {
                                R.string.settings_steam_time_action_idle
                            },
                        ),
                    )
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_cloud_backup_title),
                description = stringResource(R.string.settings_cloud_backup_description),
            ) {
                Button(
                    onClick = onOpenCloudBackupStatus,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_cloud_backup_open_status))
                }
                OutlinedButton(
                    onClick = onOpenCloudBackupConfig,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_cloud_backup_open_config))
                }
                Text(
                    text = stringResource(R.string.settings_cloud_backup_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_local_backup_title),
                description = stringResource(R.string.settings_local_backup_description),
            ) {
                OutlinedButton(
                    onClick = onOpenBackupExport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_local_backup_export))
                }
                OutlinedButton(
                    onClick = onOpenBackupRestore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_local_backup_restore))
                }
                Text(
                    text = stringResource(R.string.settings_local_backup_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_platform_security_note),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onLockVault,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_lock_now_action))
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

private fun autoLockLabel(
    context: android.content.Context,
    option: AutoLockTimeoutOption,
): String {
    return when (option) {
        AutoLockTimeoutOption.DISABLED -> context.getString(R.string.settings_auto_lock_disabled)
        AutoLockTimeoutOption.IMMEDIATELY -> context.getString(R.string.settings_auto_lock_immediately)
        AutoLockTimeoutOption.THIRTY_SECONDS -> context.getString(R.string.settings_auto_lock_thirty_seconds)
        AutoLockTimeoutOption.ONE_MINUTE -> context.getString(R.string.settings_auto_lock_one_minute)
        AutoLockTimeoutOption.FIVE_MINUTES -> context.getString(R.string.settings_auto_lock_five_minutes)
    }
}

private fun formatOffsetSeconds(
    context: android.content.Context,
    offsetSeconds: Long,
): String {
    return when {
        offsetSeconds > 0L -> context.getString(R.string.common_offset_positive, offsetSeconds)
        offsetSeconds < 0L -> context.getString(R.string.common_offset_negative, offsetSeconds)
        else -> context.getString(R.string.common_offset_zero)
    }
}
