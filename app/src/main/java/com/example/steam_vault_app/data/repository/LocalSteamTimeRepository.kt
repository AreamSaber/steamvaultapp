package com.example.steam_vault_app.data.repository

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.domain.repository.SteamTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSteamTimeRepository(
    context: Context,
) : SteamTimeRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.STEAM_TIME_PREFS,
        Context.MODE_PRIVATE,
    )

    override suspend fun getState(): SteamTimeSyncState = withContext(Dispatchers.IO) {
        SteamTimeSyncState(
            status = SteamTimeSyncStatus.entries.firstOrNull {
                it.name == prefs.getString(
                    SteamVaultPreferenceKeys.KEY_STEAM_TIME_STATUS,
                    SteamTimeSyncStatus.IDLE.name,
                )
            } ?: SteamTimeSyncStatus.IDLE,
            offsetSeconds = prefs.getLong(SteamVaultPreferenceKeys.KEY_STEAM_TIME_OFFSET_SECONDS, 0L),
            lastSyncAt = prefs.getString(SteamVaultPreferenceKeys.KEY_STEAM_TIME_LAST_SYNC_AT, null),
            lastServerTimeSeconds = prefs.getString(
                SteamVaultPreferenceKeys.KEY_STEAM_TIME_LAST_SERVER_SECONDS,
                null,
            )?.toLongOrNull(),
            lastErrorMessage = prefs.getString(
                SteamVaultPreferenceKeys.KEY_STEAM_TIME_LAST_ERROR_MESSAGE,
                null,
            ),
        )
    }

    override suspend fun saveState(state: SteamTimeSyncState) = withContext(Dispatchers.IO) {
        val saved = prefs.edit()
            .putString(SteamVaultPreferenceKeys.KEY_STEAM_TIME_STATUS, state.status.name)
            .putLong(SteamVaultPreferenceKeys.KEY_STEAM_TIME_OFFSET_SECONDS, state.offsetSeconds)
            .putString(SteamVaultPreferenceKeys.KEY_STEAM_TIME_LAST_SYNC_AT, state.lastSyncAt)
            .putString(
                SteamVaultPreferenceKeys.KEY_STEAM_TIME_LAST_SERVER_SECONDS,
                state.lastServerTimeSeconds?.toString(),
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_STEAM_TIME_LAST_ERROR_MESSAGE,
                state.lastErrorMessage,
            )
            .commit()

        if (!saved) {
            throw IllegalStateException(appContext.getString(R.string.repository_steam_time_state_save_failed))
        }
    }
}
