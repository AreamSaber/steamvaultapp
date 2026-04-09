package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.AppSecuritySettings

interface SecuritySettingsRepository {
    suspend fun getSettings(): AppSecuritySettings

    suspend fun saveSettings(settings: AppSecuritySettings)
}
