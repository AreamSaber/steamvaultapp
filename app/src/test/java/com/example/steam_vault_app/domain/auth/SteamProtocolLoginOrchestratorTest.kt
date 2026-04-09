package com.example.steam_vault_app.domain.auth

import com.example.steam_vault_app.domain.model.SteamGuardAccountSnapshot
import com.example.steam_vault_app.domain.model.SteamGuardDataRecord
import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge.QrCode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.model.SteamProtocolLoginResult
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import com.example.steam_vault_app.domain.repository.SteamGuardDataRepository
import com.example.steam_vault_app.domain.repository.SteamProtocolLoginRepository
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamProtocolLoginOrchestratorTest {
    @Test
    fun login_reusesStoredGuardData_andPersistsNewGuardData() = runBlocking {
        val sessionRepository = FakeSteamSessionRepository()
        val guardDataRepository = FakeSteamGuardDataRepository()
        guardDataRepository.saveGuardData(
            SteamGuardDataRecord(
                accountName = "demo-account",
                guardData = "stored-guard",
                updatedAt = "2026-04-09T02:00:00Z",
            ),
        )
        val protocolLoginRepository = FakeSteamProtocolLoginRepository(
            loginResult = SteamProtocolLoginResult(
                session = SteamMobileSession(
                    steamId = "76561198000000000",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
                accountNameHint = "demo-account",
                newGuardData = "fresh-guard",
            ),
        )
        val orchestrator = SteamProtocolLoginOrchestrator(
            steamProtocolLoginRepository = protocolLoginRepository,
            steamSessionRepository = sessionRepository,
            steamGuardDataRepository = guardDataRepository,
        )

        val result = orchestrator.login(
            request = SteamProtocolLoginRequest(
                username = "demo-account",
                password = "password",
            ),
            respondToChallenge = { SteamProtocolLoginChallengeAnswer.Cancelled },
        )

        assertEquals("stored-guard", protocolLoginRepository.lastLoginRequest?.guardData)
        assertEquals("fresh-guard", result.session.guardData)
        assertEquals(
            "fresh-guard",
            guardDataRepository.getGuardData(
                accountName = "demo-account",
                steamId = "76561198000000000",
            ),
        )
    }

    @Test
    fun saveSessionForToken_keepsCreatedAt_andPersistsGuardData() = runBlocking {
        val sessionRepository = FakeSteamSessionRepository(
            initialSessions = listOf(
                SteamSessionRecord(
                    tokenId = "token-1",
                    accountName = "demo-account",
                    steamId = "76561198000000000",
                    accessToken = "old-access-token",
                    refreshToken = "old-refresh-token",
                    guardData = "existing-guard",
                    createdAt = "2026-04-09T01:00:00Z",
                    updatedAt = "2026-04-09T01:00:00Z",
                ),
            ),
        )
        val guardDataRepository = FakeSteamGuardDataRepository()
        val orchestrator = SteamProtocolLoginOrchestrator(
            steamProtocolLoginRepository = FakeSteamProtocolLoginRepository(),
            steamSessionRepository = sessionRepository,
            steamGuardDataRepository = guardDataRepository,
        )

        val record = orchestrator.saveSessionForToken(
            tokenId = "token-1",
            accountName = "demo-account",
            session = SteamMobileSession(
                steamId = "76561198000000000",
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                guardData = "new-guard",
            ),
            updatedAt = "2026-04-09T03:00:00Z",
            validationStatus = SteamSessionValidationStatus.SUCCESS,
        )

        assertEquals("2026-04-09T01:00:00Z", record.createdAt)
        assertEquals("2026-04-09T03:00:00Z", record.updatedAt)
        assertEquals("new-guard", record.guardData)
        assertEquals(record, sessionRepository.getSession("token-1"))
        assertEquals(
            "new-guard",
            guardDataRepository.getGuardData(
                accountName = "demo-account",
                steamId = "76561198000000000",
            ),
        )
    }

    @Test
    fun refreshAccessToken_keepsPreviousGuardData_whenRefreshDoesNotReturnOne() = runBlocking {
        val sessionRepository = FakeSteamSessionRepository()
        val guardDataRepository = FakeSteamGuardDataRepository()
        val protocolLoginRepository = FakeSteamProtocolLoginRepository(
            refreshResult = SteamMobileSession(
                steamId = "76561198000000000",
                accessToken = "refreshed-access-token",
                refreshToken = "refreshed-refresh-token",
            ),
        )
        val orchestrator = SteamProtocolLoginOrchestrator(
            steamProtocolLoginRepository = protocolLoginRepository,
            steamSessionRepository = sessionRepository,
            steamGuardDataRepository = guardDataRepository,
        )

        val refreshed = orchestrator.refreshAccessToken(
            session = SteamMobileSession(
                steamId = "76561198000000000",
                accessToken = "old-access-token",
                refreshToken = "old-refresh-token",
                guardData = "existing-guard",
            ),
            accountName = "demo-account",
        )

        assertEquals("existing-guard", refreshed.guardData)
        assertEquals("existing-guard", protocolLoginRepository.lastRefreshSession?.guardData)
        assertEquals(
            "existing-guard",
            guardDataRepository.getGuardData(
                accountName = "demo-account",
                steamId = "76561198000000000",
            ),
        )
    }

    private class FakeSteamSessionRepository(
        initialSessions: List<SteamSessionRecord> = emptyList(),
    ) : SteamSessionRepository {
        private val sessions = LinkedHashMap<String, SteamSessionRecord>().apply {
            initialSessions.forEach { put(it.tokenId, it) }
        }

        override suspend fun getSession(tokenId: String): SteamSessionRecord? = sessions[tokenId]

        override suspend fun getSessions(): List<SteamSessionRecord> = sessions.values.toList()

        override suspend fun saveSession(session: SteamSessionRecord) {
            sessions[session.tokenId] = session
        }

        override suspend fun clearSession(tokenId: String) {
            sessions.remove(tokenId)
        }

        override suspend fun clearAllSessions() {
            sessions.clear()
        }
    }

    private class FakeSteamGuardDataRepository : SteamGuardDataRepository {
        private val records = mutableListOf<SteamGuardDataRecord>()

        override suspend fun getGuardData(accountName: String?, steamId: String?): String? {
            val normalizedSteamId = steamId?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedSteamId != null) {
                records.lastOrNull { it.steamId == normalizedSteamId }?.let { return it.guardData }
            }

            val normalizedAccountName = accountName?.trim()?.takeIf { it.isNotEmpty() }
            return normalizedAccountName?.let { name ->
                records.lastOrNull {
                    it.accountName?.equals(name, ignoreCase = true) == true
                }?.guardData
            }
        }

        override suspend fun saveGuardData(record: SteamGuardDataRecord) {
            records.removeAll {
                (record.steamId != null && it.steamId == record.steamId) ||
                    (record.accountName != null &&
                        it.accountName?.equals(record.accountName, ignoreCase = true) == true)
            }
            records += record
        }

        override suspend fun clearGuardData(accountName: String?, steamId: String?) {
            val normalizedSteamId = steamId?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedAccountName = accountName?.trim()?.takeIf { it.isNotEmpty() }
            records.removeAll {
                (normalizedSteamId != null && it.steamId == normalizedSteamId) ||
                    (normalizedAccountName != null &&
                        it.accountName?.equals(normalizedAccountName, ignoreCase = true) == true)
            }
        }
    }

    private class FakeSteamProtocolLoginRepository(
        private val loginResult: SteamProtocolLoginResult? = null,
        private val refreshResult: SteamMobileSession? = null,
    ) : SteamProtocolLoginRepository {
        var lastLoginRequest: SteamProtocolLoginRequest? = null
            private set
        var lastRefreshSession: SteamMobileSession? = null
            private set

        override suspend fun login(
            request: SteamProtocolLoginRequest,
            respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
            onQrChallengeChanged: suspend (QrCode) -> Unit,
        ): SteamProtocolLoginResult {
            lastLoginRequest = request
            return loginResult ?: error("No loginResult configured")
        }

        override suspend fun refreshAccessToken(
            session: SteamMobileSession,
            allowRefreshTokenRenewal: Boolean,
        ): SteamMobileSession {
            lastRefreshSession = session
            return refreshResult ?: session
        }
    }
}
