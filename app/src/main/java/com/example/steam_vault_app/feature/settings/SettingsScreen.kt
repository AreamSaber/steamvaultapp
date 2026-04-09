package com.example.steam_vault_app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton

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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.settings_modern_title),
                subtitle = stringResource(R.string.settings_modern_body),
            )
        }
        biometricStatusMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = if (biometricQuickUnlockAvailable) {
                        VaultBannerTone.Neutral
                    } else {
                        VaultBannerTone.Warning
                    },
                )
            }
        }
        securityStatusMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Success,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_modern_security_title),
                description = stringResource(R.string.settings_modern_security_body),
            ) {
                SettingToggleRow(
                    label = stringResource(R.string.settings_modern_biometric),
                    supporting = biometricStatusMessage
                        ?: stringResource(R.string.settings_biometric_default_status),
                    checked = securitySettings.biometricQuickUnlockEnabled,
                    enabled = biometricQuickUnlockAvailable || securitySettings.biometricQuickUnlockEnabled,
                    onCheckedChange = onBiometricQuickUnlockToggle,
                )
                SettingToggleRow(
                    label = stringResource(R.string.settings_modern_secure_screen),
                    supporting = stringResource(R.string.settings_modern_secure_screen_body),
                    checked = securitySettings.secureScreensEnabled,
                    onCheckedChange = { enabled ->
                        onSecuritySettingsChanged(
                            securitySettings.copy(secureScreensEnabled = enabled),
                        )
                    },
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.settings_modern_change_password),
                    onClick = onOpenChangePassword,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.settings_modern_lock_now),
                    onClick = onLockVault,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_modern_time_title),
                description = stringResource(R.string.settings_modern_time_body),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_steam_time_current_offset,
                        formatOffsetSeconds(context, steamTimeSyncState.offsetSeconds),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.settings_steam_time_last_sync,
                        steamTimeSyncState.lastSyncAt
                            ?: stringResource(R.string.settings_steam_time_last_sync_none),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                steamTimeStatusText(
                    state = steamTimeSyncState,
                    isSyncing = isSyncingSteamTime,
                )?.let { statusText ->
                    VaultInlineBanner(
                        text = statusText,
                        tone = if (steamTimeSyncState.status == SteamTimeSyncStatus.ERROR) {
                            VaultBannerTone.Error
                        } else {
                            VaultBannerTone.Neutral
                        },
                    )
                }
                VaultPrimaryButton(
                    text = stringResource(
                        if (isSyncingSteamTime) {
                            R.string.settings_steam_time_action_loading
                        } else {
                            R.string.settings_modern_sync_now
                        },
                    ),
                    onClick = onSyncSteamTime,
                    enabled = !isSyncingSteamTime,
                )
                AutoLockTimeoutOption.entries.forEach { option ->
                    VaultSecondaryButton(
                        text = autoLockLabel(context, option),
                        onClick = {
                            onSecuritySettingsChanged(
                                securitySettings.copy(autoLockTimeout = option),
                            )
                        },
                    )
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_modern_backup_title),
                description = stringResource(R.string.settings_modern_backup_body),
            ) {
                VaultSecondaryButton(
                    text = stringResource(R.string.settings_modern_export),
                    onClick = onOpenBackupExport,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.settings_modern_restore),
                    onClick = onOpenBackupRestore,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.settings_modern_cloud_title),
                description = stringResource(R.string.settings_modern_cloud_body),
            ) {
                VaultPrimaryButton(
                    text = stringResource(R.string.settings_modern_cloud_status),
                    onClick = onOpenCloudBackupStatus,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.settings_modern_cloud_config),
                    onClick = onOpenCloudBackupConfig,
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun steamTimeStatusText(
    state: SteamTimeSyncState,
    isSyncing: Boolean,
): String? {
    return when {
        isSyncing -> stringResource(R.string.settings_steam_time_syncing)
        state.status == SteamTimeSyncStatus.ERROR -> {
            state.lastErrorMessage ?: stringResource(R.string.settings_steam_time_idle)
        }

        state.status == SteamTimeSyncStatus.SUCCESS -> stringResource(R.string.settings_steam_time_success)
        else -> stringResource(R.string.settings_steam_time_idle)
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
