package com.example.steam_vault_app.data.repository

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.repository.SecuritySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSecuritySettingsRepository(
    context: Context,
    private val autoCloudBackupScheduler: AutoCloudBackupScheduler? = null,
) : SecuritySettingsRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.SECURITY_PREFS,
        Context.MODE_PRIVATE,
    )

    override suspend fun getSettings(): AppSecuritySettings = withContext(Dispatchers.IO) {
        AppSecuritySettings(
            secureScreensEnabled = prefs.getBoolean(
                SteamVaultPreferenceKeys.KEY_SECURE_SCREENS_ENABLED,
                true,
            ),
            biometricQuickUnlockEnabled = prefs.getBoolean(
                SteamVaultPreferenceKeys.KEY_BIOMETRIC_QUICK_UNLOCK_ENABLED,
                false,
            ),
            autoLockTimeout = AutoLockTimeoutOption.fromPreferenceValue(
                prefs.getString(
                    SteamVaultPreferenceKeys.KEY_AUTO_LOCK_TIMEOUT,
                    AutoLockTimeoutOption.default.preferenceValue,
                ),
            ),
        )
    }

    override suspend fun saveSettings(settings: AppSecuritySettings) = withContext(Dispatchers.IO) {
        val saved = prefs.edit()
            .putBoolean(
                SteamVaultPreferenceKeys.KEY_SECURE_SCREENS_ENABLED,
                settings.secureScreensEnabled,
            )
            .putBoolean(
                SteamVaultPreferenceKeys.KEY_BIOMETRIC_QUICK_UNLOCK_ENABLED,
                settings.biometricQuickUnlockEnabled,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_AUTO_LOCK_TIMEOUT,
                settings.autoLockTimeout.preferenceValue,
            )
            .commit()

        if (!saved) {
            throw IllegalStateException(appContext.getString(R.string.repository_security_settings_save_failed))
        }

        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.SECURITY_SETTINGS_CHANGED)
        Unit
    }
}
