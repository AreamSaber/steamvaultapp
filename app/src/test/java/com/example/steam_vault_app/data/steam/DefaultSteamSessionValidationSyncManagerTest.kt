package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSteamSessionValidationSyncManagerTest {
    @Test
    fun validateSession_success_updatesValidationMetadataAndSteamId() = runBlocking {
        val repository = FakeSteamSessionRepository(
            SteamSessionRecord(
                tokenId = "token-1",
                accountName = "demo",
                steamId = null,
                sessionId = "session-id",
                cookies = listOf(
                    SteamSessionCookie("steamLoginSecure", "secure-cookie"),
                ),
                createdAt = "2026-04-08T10:00:00Z",
                updatedAt = "2026-04-08T10:01:00Z",
            ),
        )
        val manager = DefaultSteamSessionValidationSyncManager(
            steamSessionRepository = repository,
            apiClient = FakeSteamSessionValidationApiClient(
                result = SteamSessionValidationResult(
                    resolvedSteamId = "76561198000000001",
                    profileUrl = "https://steamcommunity.com/profiles/76561198000000001",
                ),
            ),
            nowIsoUtc = { "2026-04-08T12:00:00Z" },
        )

        val session = manager.validateSession("token-1")

        assertEquals("76561198000000001", session.steamId)
        assertEquals("2026-04-08T12:00:00Z", session.lastValidatedAt)
        assertEquals("2026-04-08T12:00:00Z", session.updatedAt)
        assertEquals(SteamSessionValidationStatus.SUCCESS, session.validationStatus)
        assertEquals(null, session.lastValidationErrorMessage)
        assertTrue(session.cookies.any { it.name == "sessionid" && it.value == "session-id" })
        assertEquals(session, repository.savedSessions.last())
    }

    @Test
    fun validateSession_failure_persistsErrorStateBeforeThrowing() = runBlocking {
        val repository = FakeSteamSessionRepository(
            SteamSessionRecord(
                tokenId = "token-1",
                accountName = "demo",
                steamId = "76561198000000001",
                sessionId = "session-id",
                cookies = listOf(
                    SteamSessionCookie("steamLoginSecure", "secure-cookie"),
                    SteamSessionCookie("sessionid", "session-id"),
                ),
                createdAt = "2026-04-08T10:00:00Z",
                updatedAt = "2026-04-08T10:01:00Z",
            ),
        )
        val manager = DefaultSteamSessionValidationSyncManager(
            steamSessionRepository = repository,
            apiClient = FakeSteamSessionValidationApiClient(
                errorMessage = "Steam session expired",
            ),
            nowIsoUtc = { "2026-04-08T12:30:00Z" },
        )

        val error = runCatching {
            manager.validateSession("token-1")
        }.exceptionOrNull()

        requireNotNull(error)
        assertEquals("Steam session expired", error.message)
        val persisted = repository.savedSessions.last()
        assertEquals("2026-04-08T12:30:00Z", persisted.lastValidatedAt)
        assertEquals(SteamSessionValidationStatus.ERROR, persisted.validationStatus)
        assertEquals("Steam session expired", persisted.lastValidationErrorMessage)
    }

    private class FakeSteamSessionRepository(
        initialSession: SteamSessionRecord?,
    ) : SteamSessionRepository {
        private var session: SteamSessionRecord? = initialSession
        val savedSessions = mutableListOf<SteamSessionRecord>()

        override suspend fun getSession(tokenId: String): SteamSessionRecord? {
            return session?.takeIf { it.tokenId == tokenId }
        }

        override suspend fun getSessions(): List<SteamSessionRecord> = listOfNotNull(session)

        override suspend fun saveSession(session: SteamSessionRecord) {
            this.session = session
            savedSessions += session
        }

        override suspend fun clearSession(tokenId: String) {
            session = null
        }

        override suspend fun clearAllSessions() {
            session = null
        }
    }

    private class FakeSteamSessionValidationApiClient(
        private val result: SteamSessionValidationResult? = null,
        private val errorMessage: String? = null,
    ) : SteamSessionValidationApiClient {
        override suspend fun validateSession(
            request: SteamSessionValidationRequest,
        ): SteamSessionValidationResult {
            errorMessage?.let { throw IllegalStateException(it) }
            return result ?: error("Missing fake validation result.")
        }
    }
}
