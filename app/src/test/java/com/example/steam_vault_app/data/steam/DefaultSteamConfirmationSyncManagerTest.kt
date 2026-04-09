package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.model.ImportDraft
import com.example.steam_vault_app.domain.model.LocalBackupPackage
import com.example.steam_vault_app.domain.model.SteamConfirmation
import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.model.VaultBlob
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.SteamTimeRepository
import com.example.steam_vault_app.domain.repository.SteamGuardDataRepository
import com.example.steam_vault_app.domain.repository.SteamProtocolLoginRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSteamConfirmationSyncManagerTest {
    @Test
    fun fetchConfirmations_buildsSignedRequestWithSyncedTimeAndNormalizedCookies() = runBlocking {
        val token = demoToken()
        val session = SteamSessionRecord(
            tokenId = token.id,
            accountName = token.accountName,
            steamId = null,
            sessionId = "session-from-field",
            cookies = listOf(
                SteamSessionCookie(
                    name = "steamLoginSecure",
                    value = "76561198000000001%7C%7Csecure-cookie",
                ),
            ),
            createdAt = "2026-04-08T10:00:00Z",
            updatedAt = "2026-04-08T10:01:00Z",
        )
        val apiClient = FakeSteamConfirmationApiClient(
            fetchResult = listOf(
                SteamConfirmation(
                    id = "1001",
                    nonce = "nonce-1001",
                    headline = "Trade Offer",
                ),
            ),
        )
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = FakeSteamSessionRepository(session),
            steamTimeRepository = FakeSteamTimeRepository(
                SteamTimeSyncState(
                    status = SteamTimeSyncStatus.SUCCESS,
                    offsetSeconds = 15L,
                    lastSyncAt = "2026-04-08T11:59:00Z",
                ),
            ),
            apiClient = apiClient,
            currentEpochSeconds = { 1_700_000_000L },
        )

        val confirmations = manager.fetchConfirmations(token.id)

        assertEquals(1, confirmations.size)
        val request = apiClient.fetchRequests.single()
        assertEquals("76561198000000001", request.steamId)
        assertEquals(token.deviceId, request.deviceId)
        assertEquals("conf", request.tag)
        assertEquals(1_700_000_015L, request.timestampSeconds)
        assertEquals(
            SteamConfirmationSigner.generateConfirmationKey(
                identitySecret = token.identitySecret.orEmpty(),
                timestampSeconds = 1_700_000_015L,
                tag = "conf",
            ),
            request.confirmationKey,
        )
        assertTrue(
            request.cookies.any { cookie ->
                cookie.name == "sessionid" && cookie.value == "session-from-field"
            },
        )
    }

    @Test
    fun approveConfirmation_usesAcceptTagThenRefreshesConfirmationList() = runBlocking {
        val token = demoToken()
        val session = demoSession(token.id)
        val apiClient = FakeSteamConfirmationApiClient(
            fetchResult = listOf(
                SteamConfirmation(
                    id = "1002",
                    nonce = "nonce-1002",
                    headline = "Market Listing",
                ),
            ),
        )
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = FakeSteamSessionRepository(session),
            steamTimeRepository = FakeSteamTimeRepository(
                SteamTimeSyncState(
                    status = SteamTimeSyncStatus.SUCCESS,
                    offsetSeconds = 0L,
                    lastSyncAt = "2026-04-08T11:59:00Z",
                ),
            ),
            apiClient = apiClient,
            currentEpochSeconds = { 1_700_000_100L },
        )

        val confirmations = manager.approveConfirmation(
            tokenId = token.id,
            confirmationId = "1002",
            confirmationNonce = "nonce-1002",
        )

        assertEquals("Market Listing", confirmations.single().headline)
        val action = apiClient.resolveCalls.single()
        assertEquals("accept", action.request.tag)
        assertEquals("1002", action.confirmationId)
        assertEquals("nonce-1002", action.confirmationNonce)
        assertTrue(action.approve)
        assertEquals("conf", apiClient.fetchRequests.last().tag)
    }

    @Test
    fun rejectConfirmation_usesRejectTagThenRefreshesConfirmationList() = runBlocking {
        val token = demoToken()
        val session = demoSession(token.id)
        val apiClient = FakeSteamConfirmationApiClient(
            fetchResult = listOf(
                SteamConfirmation(
                    id = "1003",
                    nonce = "nonce-1003",
                    headline = "Login Attempt",
                ),
            ),
        )
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = FakeSteamSessionRepository(session),
            steamTimeRepository = FakeSteamTimeRepository(
                SteamTimeSyncState(
                    status = SteamTimeSyncStatus.SUCCESS,
                    offsetSeconds = 0L,
                    lastSyncAt = "2026-04-08T11:59:00Z",
                ),
            ),
            apiClient = apiClient,
            currentEpochSeconds = { 1_700_000_100L },
        )

        val confirmations = manager.rejectConfirmation(
            tokenId = token.id,
            confirmationId = "1003",
            confirmationNonce = "nonce-1003",
        )

        assertEquals("Login Attempt", confirmations.single().headline)
        val action = apiClient.resolveCalls.single()
        assertEquals("reject", action.request.tag)
        assertEquals("1003", action.confirmationId)
        assertEquals("nonce-1003", action.confirmationNonce)
        assertTrue(!action.approve)
        assertEquals("conf", apiClient.fetchRequests.last().tag)
    }

    @Test
    fun fetchConfirmations_withoutSuccessfulTimeSync_throws() = runBlocking {
        val token = demoToken()
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = FakeSteamSessionRepository(demoSession(token.id)),
            steamTimeRepository = FakeSteamTimeRepository(SteamTimeSyncState()),
            apiClient = FakeSteamConfirmationApiClient(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                manager.fetchConfirmations(token.id)
            }
        }

        assertEquals("Steam time must be synced before loading confirmations.", error.message)
    }

    @Test
    fun fetchConfirmations_refreshesSessionWhenCookiesAreMissing() = runBlocking {
        val token = demoToken()
        val sessionRepository = FakeSteamSessionRepository(
            SteamSessionRecord(
                tokenId = token.id,
                accountName = token.accountName,
                steamId = "76561198000000001",
                accessToken = "stale-access",
                refreshToken = "refresh-token",
                cookies = emptyList(),
                createdAt = "2026-04-08T10:00:00Z",
                updatedAt = "2026-04-08T10:01:00Z",
                validationStatus = SteamSessionValidationStatus.SUCCESS,
            ),
        )
        val apiClient = FakeSteamConfirmationApiClient(
            fetchResult = listOf(
                SteamConfirmation(
                    id = "1004",
                    nonce = "nonce-1004",
                    headline = "Recovered Trade",
                ),
            ),
        )
        val refreshedSession = SteamMobileSession(
            steamId = "76561198000000001",
            accessToken = "fresh-access",
            refreshToken = "refresh-token",
            sessionId = "fresh-session-id",
            oauthToken = "fresh-access",
            cookies = listOf(
                SteamSessionCookie("steamLoginSecure", "76561198000000001%7C%7Cfresh-access"),
                SteamSessionCookie("sessionid", "fresh-session-id"),
            ),
        )
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = sessionRepository,
            steamTimeRepository = FakeSteamTimeRepository(
                SteamTimeSyncState(
                    status = SteamTimeSyncStatus.SUCCESS,
                    offsetSeconds = 0L,
                    lastSyncAt = "2026-04-08T11:59:00Z",
                ),
            ),
            apiClient = apiClient,
            steamProtocolLoginOrchestrator = fakeOrchestrator(
                sessionRepository = sessionRepository,
                refreshedSession = refreshedSession,
            ),
            currentEpochSeconds = { 1_700_000_100L },
        )

        val confirmations = manager.fetchConfirmations(token.id)

        assertEquals("Recovered Trade", confirmations.single().headline)
        val request = apiClient.fetchRequests.single()
        assertTrue(request.cookies.any { it.name == "steamLoginSecure" && it.value.contains("fresh-access") })
        assertTrue(request.cookies.any { it.name == "sessionid" && it.value == "fresh-session-id" })
        assertEquals("fresh-access", sessionRepository.getSession(token.id)?.accessToken)
    }

    @Test
    fun fetchConfirmations_retriesOnceAfterSessionExpiredResponse() = runBlocking {
        val token = demoToken()
        val sessionRepository = FakeSteamSessionRepository(demoSession(token.id).copy(
            accessToken = "stale-access",
            refreshToken = "refresh-token",
        ))
        val apiClient = FakeSteamConfirmationApiClient(
            fetchResult = listOf(
                SteamConfirmation(
                    id = "1005",
                    nonce = "nonce-1005",
                    headline = "Recovered After Expiry",
                ),
            ),
            failFetchOnceWith = IllegalStateException("Steam session has expired. Please log in again."),
        )
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = sessionRepository,
            steamTimeRepository = FakeSteamTimeRepository(
                SteamTimeSyncState(
                    status = SteamTimeSyncStatus.SUCCESS,
                    offsetSeconds = 0L,
                    lastSyncAt = "2026-04-08T11:59:00Z",
                ),
            ),
            apiClient = apiClient,
            steamProtocolLoginOrchestrator = fakeOrchestrator(
                sessionRepository = sessionRepository,
                refreshedSession = SteamMobileSession(
                    steamId = "76561198000000001",
                    accessToken = "fresh-access-2",
                    refreshToken = "refresh-token",
                    sessionId = "fresh-session-id-2",
                    oauthToken = "fresh-access-2",
                    cookies = listOf(
                        SteamSessionCookie("steamLoginSecure", "76561198000000001%7C%7Cfresh-access-2"),
                        SteamSessionCookie("sessionid", "fresh-session-id-2"),
                    ),
                ),
            ),
            currentEpochSeconds = { 1_700_000_100L },
        )

        val confirmations = manager.fetchConfirmations(token.id)

        assertEquals("Recovered After Expiry", confirmations.single().headline)
        assertEquals(2, apiClient.fetchRequests.size)
        assertEquals("fresh-access-2", sessionRepository.getSession(token.id)?.accessToken)
    }

    @Test
    fun approveConfirmation_retriesOnceAfterSessionExpiredResponse() = runBlocking {
        val token = demoToken()
        val sessionRepository = FakeSteamSessionRepository(demoSession(token.id).copy(
            accessToken = "stale-access",
            refreshToken = "refresh-token",
        ))
        val apiClient = FakeSteamConfirmationApiClient(
            fetchResult = listOf(
                SteamConfirmation(
                    id = "1006",
                    nonce = "nonce-1006",
                    headline = "Recovered Approval",
                ),
            ),
            failResolveOnceWith = IllegalStateException("Steam confirmation action requires authentication."),
        )
        val manager = DefaultSteamConfirmationSyncManager(
            vaultRepository = FakeVaultRepository(token),
            steamSessionRepository = sessionRepository,
            steamTimeRepository = FakeSteamTimeRepository(
                SteamTimeSyncState(
                    status = SteamTimeSyncStatus.SUCCESS,
                    offsetSeconds = 0L,
                    lastSyncAt = "2026-04-08T11:59:00Z",
                ),
            ),
            apiClient = apiClient,
            steamProtocolLoginOrchestrator = fakeOrchestrator(
                sessionRepository = sessionRepository,
                refreshedSession = SteamMobileSession(
                    steamId = "76561198000000001",
                    accessToken = "fresh-access-3",
                    refreshToken = "refresh-token",
                    sessionId = "fresh-session-id-3",
                    oauthToken = "fresh-access-3",
                    cookies = listOf(
                        SteamSessionCookie("steamLoginSecure", "76561198000000001%7C%7Cfresh-access-3"),
                        SteamSessionCookie("sessionid", "fresh-session-id-3"),
                    ),
                ),
            ),
            currentEpochSeconds = { 1_700_000_100L },
        )

        val confirmations = manager.approveConfirmation(
            tokenId = token.id,
            confirmationId = "1006",
            confirmationNonce = "nonce-1006",
        )

        assertEquals("Recovered Approval", confirmations.single().headline)
        assertEquals(2, apiClient.resolveCalls.size)
        assertEquals(1, apiClient.fetchRequests.size)
        assertEquals("fresh-access-3", sessionRepository.getSession(token.id)?.accessToken)
    }

    private fun demoToken(): TokenRecord {
        return TokenRecord(
            id = "token-1",
            accountName = "Demo",
            sharedSecret = "shared-secret",
            identitySecret = "c3RlYW0=",
            deviceId = "android:demo-device",
            tokenGid = "gid-1",
            createdAt = "2026-04-08T10:00:00Z",
            updatedAt = "2026-04-08T10:00:00Z",
        )
    }

    private fun demoSession(tokenId: String): SteamSessionRecord {
        return SteamSessionRecord(
            tokenId = tokenId,
            accountName = "Demo",
            steamId = "76561198000000001",
            sessionId = "session-cookie",
            cookies = listOf(
                SteamSessionCookie("steamLoginSecure", "76561198000000001%7C%7Csecure-cookie"),
                SteamSessionCookie("sessionid", "session-cookie"),
            ),
            createdAt = "2026-04-08T10:00:00Z",
            updatedAt = "2026-04-08T10:01:00Z",
        )
    }

    private class FakeVaultRepository(
        private val token: TokenRecord?,
    ) : VaultRepository {
        override suspend fun initializeEmptyVault() = Unit

        override suspend fun hasVault(): Boolean = token != null

        override suspend fun getTokens(): List<TokenRecord> = listOfNotNull(token)

        override suspend fun getToken(tokenId: String): TokenRecord? = token?.takeIf { it.id == tokenId }

        override suspend fun saveImportedToken(importDraft: ImportDraft): TokenRecord {
            error("Unused in test.")
        }

        override suspend fun deleteToken(tokenId: String) = Unit

        override suspend fun exportVault(): VaultBlob {
            error("Unused in test.")
        }

        override suspend fun exportLocalBackup(): LocalBackupPackage {
            error("Unused in test.")
        }

        override suspend fun restoreLocalBackup(backupPackage: LocalBackupPackage) = Unit
    }

    private class FakeSteamSessionRepository(
        session: SteamSessionRecord?,
    ) : SteamSessionRepository {
        private var currentSession: SteamSessionRecord? = session

        override suspend fun getSession(tokenId: String): SteamSessionRecord? {
            return currentSession?.takeIf { it.tokenId == tokenId }
        }

        override suspend fun getSessions(): List<SteamSessionRecord> = listOfNotNull(currentSession)

        override suspend fun saveSession(session: SteamSessionRecord) {
            currentSession = session
        }

        override suspend fun clearSession(tokenId: String) {
            if (currentSession?.tokenId == tokenId) {
                currentSession = null
            }
        }

        override suspend fun clearAllSessions() {
            currentSession = null
        }
    }

    private class FakeSteamTimeRepository(
        private val state: SteamTimeSyncState,
    ) : SteamTimeRepository {
        override suspend fun getState(): SteamTimeSyncState = state

        override suspend fun saveState(state: SteamTimeSyncState) = Unit
    }

    private class FakeSteamConfirmationApiClient(
        private val fetchResult: List<SteamConfirmation> = emptyList(),
        private val failFetchOnceWith: Exception? = null,
        private val failResolveOnceWith: Exception? = null,
    ) : SteamConfirmationApiClient {
        val fetchRequests = mutableListOf<SteamConfirmationRequest>()
        val resolveCalls = mutableListOf<ResolveCall>()
        private var fetchFailureConsumed = false
        private var resolveFailureConsumed = false

        override suspend fun fetchConfirmations(
            request: SteamConfirmationRequest,
        ): List<SteamConfirmation> {
            fetchRequests += request
            if (!fetchFailureConsumed && failFetchOnceWith != null) {
                fetchFailureConsumed = true
                throw failFetchOnceWith
            }
            return fetchResult
        }

        override suspend fun resolveConfirmation(
            request: SteamConfirmationRequest,
            confirmationId: String,
            confirmationNonce: String,
            approve: Boolean,
        ) {
            resolveCalls += ResolveCall(
                request = request,
                confirmationId = confirmationId,
                confirmationNonce = confirmationNonce,
                approve = approve,
            )
            if (!resolveFailureConsumed && failResolveOnceWith != null) {
                resolveFailureConsumed = true
                throw failResolveOnceWith
            }
        }
    }

    private data class ResolveCall(
        val request: SteamConfirmationRequest,
        val confirmationId: String,
        val confirmationNonce: String,
        val approve: Boolean,
    )

    private fun fakeOrchestrator(
        sessionRepository: SteamSessionRepository,
        refreshedSession: SteamMobileSession,
    ): SteamProtocolLoginOrchestrator {
        return SteamProtocolLoginOrchestrator(
            steamProtocolLoginRepository = object : SteamProtocolLoginRepository {
                override suspend fun login(
                    request: SteamProtocolLoginRequest,
                    respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
                    onQrChallengeChanged: suspend (SteamProtocolLoginChallenge.QrCode) -> Unit,
                ): com.example.steam_vault_app.domain.model.SteamProtocolLoginResult {
                    error("Unused in test.")
                }

                override suspend fun refreshAccessToken(
                    session: SteamMobileSession,
                    allowRefreshTokenRenewal: Boolean,
                ): SteamMobileSession = refreshedSession
            },
            steamSessionRepository = sessionRepository,
            steamGuardDataRepository = object : SteamGuardDataRepository {
                override suspend fun getGuardData(accountName: String?, steamId: String?) = null
                override suspend fun saveGuardData(record: com.example.steam_vault_app.domain.model.SteamGuardDataRecord) = Unit
                override suspend fun clearGuardData(accountName: String?, steamId: String?) = Unit
            },
        )
    }
}
