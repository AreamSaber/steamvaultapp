package com.example.steam_vault_app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton

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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
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
                    tone = if (biometricQuickUnlockAvailable) VaultBannerTone.Neutral else VaultBannerTone.Warning,
                )
            }
        }
        securityStatusMessage?.let { message ->
            item {
                VaultInlineBanner(text = message, tone = VaultBannerTone.Success)
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_modern_security_title)) {
                SettingsActionRow(
                    icon = androidx.compose.material.icons.filled.Lock,
                    label = stringResource(R.string.settings_modern_change_password),
                    onClick = onOpenChangePassword
                )
                SettingsActionRow(
                    icon = androidx.compose.material.icons.filled.Lock,
                    label = stringResource(R.string.settings_modern_lock_now),
                    onClick = onLockVault,
                    showArrow = false
                )
                SettingsSwitchRow(
                    icon = androidx.compose.material.icons.filled.Person,
                    label = stringResource(R.string.settings_modern_biometric),
                    checked = securitySettings.biometricQuickUnlockEnabled,
                    onCheckedChange = onBiometricQuickUnlockToggle,
                    enabled = biometricQuickUnlockAvailable || securitySettings.biometricQuickUnlockEnabled
                )
                SettingsSwitchRow(
                    icon = androidx.compose.material.icons.filled.Lock,
                    label = stringResource(R.string.settings_modern_secure_screen),
                    checked = securitySettings.secureScreensEnabled,
                    onCheckedChange = { enabled ->
                        onSecuritySettingsChanged(securitySettings.copy(secureScreensEnabled = enabled))
                    }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_modern_time_title)) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "当前偏移量",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatOffsetSeconds(context, steamTimeSyncState.offsetSeconds),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "上次同步",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = steamTimeSyncState.lastSyncAt ?: stringResource(R.string.settings_steam_time_last_sync_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    steamTimeStatusText(state = steamTimeSyncState, isSyncing = isSyncingSteamTime)?.let { statusText ->
                        VaultInlineBanner(
                            text = statusText,
                            tone = if (steamTimeSyncState.status == SteamTimeSyncStatus.ERROR) VaultBannerTone.Error else VaultBannerTone.Neutral,
                        )
                    }
                    
                    VaultPrimaryButton(
                        text = stringResource(if (isSyncingSteamTime) R.string.settings_steam_time_action_loading else R.string.settings_modern_sync_now),
                        onClick = onSyncSteamTime,
                        enabled = !isSyncingSteamTime,
                        leadingIcon = Icons.Default.Sync
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_modern_backup_title)) {
                SettingsActionRow(
                    icon = androidx.compose.material.icons.filled.KeyboardArrowDown,
                    label = stringResource(R.string.settings_modern_export),
                    onClick = onOpenBackupExport
                )
                SettingsActionRow(
                    icon = androidx.compose.material.icons.filled.Refresh,
                    label = stringResource(R.string.settings_modern_restore),
                    onClick = onOpenBackupRestore
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_modern_cloud_title)) {
                SettingsActionRow(
                    icon = androidx.compose.material.icons.filled.CloudSync,
                    label = stringResource(R.string.settings_modern_cloud_status),
                    onClick = onOpenCloudBackupStatus
                )
                SettingsActionRow(
                    icon = androidx.compose.material.icons.filled.Cloud,
                    label = stringResource(R.string.settings_modern_cloud_config),
                    onClick = onOpenCloudBackupConfig
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    showArrow: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showArrow) {
            Icon(
                imageVector = androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            )
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
