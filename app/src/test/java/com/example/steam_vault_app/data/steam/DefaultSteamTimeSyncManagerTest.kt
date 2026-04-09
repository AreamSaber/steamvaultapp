package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.domain.repository.SteamTimeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSteamTimeSyncManagerTest {
    @Test
    fun syncNow_updatesOffsetAndTimestampOnSuccess() = runBlocking {
        val repository = FakeSteamTimeRepository()
        val manager = DefaultSteamTimeSyncManager(
            steamTimeRepository = repository,
            steamTimeApiClient = FakeSteamTimeApiClient(serverTimeSeconds = 1_700_000_015L),
            nowIsoUtc = { "2026-04-07T18:00:00Z" },
            currentEpochSeconds = { 1_700_000_000L },
        )

        val state = manager.syncNow()

        assertEquals(SteamTimeSyncStatus.SUCCESS, state.status)
        assertEquals(15L, state.offsetSeconds)
        assertEquals("2026-04-07T18:00:00Z", state.lastSyncAt)
        assertEquals(state, repository.state)
    }

    @Test
    fun syncNow_preservesPreviousOffsetOnFailure() = runBlocking {
        val repository = FakeSteamTimeRepository(
            SteamTimeSyncState(
                status = SteamTimeSyncStatus.SUCCESS,
                offsetSeconds = 8L,
                lastSyncAt = "2026-04-07T17:59:00Z",
            ),
        )
        val manager = DefaultSteamTimeSyncManager(
            steamTimeRepository = repository,
            steamTimeApiClient = FakeSteamTimeApiClient(errorMessage = "Steam 时间接口超时。"),
            nowIsoUtc = { "2026-04-07T18:00:00Z" },
            currentEpochSeconds = { 1_700_000_000L },
        )

        val state = manager.syncNow()

        assertEquals(SteamTimeSyncStatus.ERROR, state.status)
        assertEquals(8L, state.offsetSeconds)
        assertEquals("Steam 时间接口超时。", state.lastErrorMessage)
        assertEquals(state, repository.state)
    }

    private class FakeSteamTimeRepository(
        var state: SteamTimeSyncState = SteamTimeSyncState(),
    ) : SteamTimeRepository {
        override suspend fun getState(): SteamTimeSyncState = state

        override suspend fun saveState(state: SteamTimeSyncState) {
            this.state = state
        }
    }

    private class FakeSteamTimeApiClient(
        private val serverTimeSeconds: Long? = null,
        private val errorMessage: String? = null,
    ) : SteamTimeApiClient {
        override suspend fun queryServerTimeSeconds(): Long {
            errorMessage?.let { throw IllegalStateException(it) }
            return serverTimeSeconds ?: error("Missing fake server time.")
        }
    }
}
