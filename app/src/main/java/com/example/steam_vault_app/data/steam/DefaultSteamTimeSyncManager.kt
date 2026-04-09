package com.example.steam_vault_app.data.steam

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.domain.repository.SteamTimeRepository
import com.example.steam_vault_app.domain.sync.SteamTimeSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultSteamTimeSyncManager(
    private val steamTimeRepository: SteamTimeRepository,
    private val steamTimeApiClient: SteamTimeApiClient = OkHttpSteamTimeApiClient(),
    private val nowIsoUtc: () -> String = ::defaultNowIsoUtc,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    context: Context? = null,
) : SteamTimeSyncManager {
    private val genericFailureMessage = context?.applicationContext?.getString(R.string.steam_time_sync_failed_generic)
        ?: "Unable to sync Steam time."

    override suspend fun syncNow(): SteamTimeSyncState = withContext(Dispatchers.IO) {
        val currentState = steamTimeRepository.getState()

        try {
            val deviceTimeSeconds = currentEpochSeconds()
            val serverTimeSeconds = steamTimeApiClient.queryServerTimeSeconds()
            val offsetSeconds = SteamTimeOffsetCalculator.calculateOffsetSeconds(
                serverTimeSeconds = serverTimeSeconds,
                deviceTimeSeconds = deviceTimeSeconds,
            )
            val nextState = currentState.copy(
                status = SteamTimeSyncStatus.SUCCESS,
                offsetSeconds = offsetSeconds,
                lastSyncAt = nowIsoUtc(),
                lastServerTimeSeconds = serverTimeSeconds,
                lastErrorMessage = null,
            )
            steamTimeRepository.saveState(nextState)
            nextState
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failedState = currentState.copy(
                status = SteamTimeSyncStatus.ERROR,
                lastErrorMessage = error.message ?: genericFailureMessage,
            )
            steamTimeRepository.saveState(failedState)
            failedState
        }
    }

    private companion object {
        private fun defaultNowIsoUtc(): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            return formatter.format(Date())
        }
    }
}
