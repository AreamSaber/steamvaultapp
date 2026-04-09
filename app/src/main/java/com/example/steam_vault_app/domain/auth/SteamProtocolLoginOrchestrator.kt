package com.example.steam_vault_app.domain.auth

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
import java.time.Instant

class SteamProtocolLoginOrchestrator(
    private val steamProtocolLoginRepository: SteamProtocolLoginRepository,
    private val steamSessionRepository: SteamSessionRepository,
    private val steamGuardDataRepository: SteamGuardDataRepository,
) {
    suspend fun login(
        request: SteamProtocolLoginRequest,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
        onQrChallengeChanged: suspend (QrCode) -> Unit = {},
    ): SteamProtocolLoginResult {
        val resolvedGuardData = resolveGuardData(request)
        val effectiveRequest = if (resolvedGuardData == request.guardData) {
            request
        } else {
            request.copy(guardData = resolvedGuardData)
        }

        val result = steamProtocolLoginRepository.login(
            effectiveRequest,
            respondToChallenge,
            onQrChallengeChanged,
        )
        val finalGuardData = result.newGuardData
            ?: result.session.guardData
            ?: resolvedGuardData

        val session = if (finalGuardData == result.session.guardData || finalGuardData.isNullOrBlank()) {
            result.session
        } else {
            result.session.copy(guardData = finalGuardData)
        }

        persistGuardData(
            accountName = result.accountNameHint ?: request.existingAccount?.accountName ?: request.username,
            steamId = session.steamId,
            guardData = finalGuardData,
        )

        return if (session == result.session) {
            result
        } else {
            result.copy(session = session)
        }
    }

    suspend fun refreshAccessToken(
        session: SteamMobileSession,
        accountName: String? = null,
        allowRefreshTokenRenewal: Boolean = false,
    ): SteamMobileSession {
        val refreshed = steamProtocolLoginRepository.refreshAccessToken(
            session = session,
            allowRefreshTokenRenewal = allowRefreshTokenRenewal,
        )
        val finalGuardData = refreshed.guardData ?: session.guardData
        val finalSession = if (finalGuardData == refreshed.guardData || finalGuardData.isNullOrBlank()) {
            refreshed
        } else {
            refreshed.copy(guardData = finalGuardData)
        }

        persistGuardData(
            accountName = accountName,
            steamId = finalSession.steamId,
            guardData = finalGuardData,
        )

        return finalSession
    }

    suspend fun saveSessionForToken(
        tokenId: String,
        accountName: String,
        session: SteamMobileSession,
        updatedAt: String = Instant.now().toString(),
        lastValidatedAt: String? = null,
        validationStatus: SteamSessionValidationStatus = SteamSessionValidationStatus.UNKNOWN,
        lastValidationErrorMessage: String? = null,
    ): SteamSessionRecord {
        val existingSession = steamSessionRepository.getSession(tokenId)
        val finalGuardData = session.guardData
            ?: existingSession?.guardData
            ?: steamGuardDataRepository.getGuardData(
                accountName = accountName,
                steamId = session.steamId,
            )
        val persistedSession = if (finalGuardData == session.guardData || finalGuardData.isNullOrBlank()) {
            session
        } else {
            session.copy(guardData = finalGuardData)
        }
        val record = persistedSession.toRecord(
            tokenId = tokenId,
            accountName = accountName,
            createdAt = existingSession?.createdAt ?: updatedAt,
            updatedAt = updatedAt,
            lastValidatedAt = lastValidatedAt,
            validationStatus = validationStatus,
            lastValidationErrorMessage = lastValidationErrorMessage,
        )

        steamSessionRepository.saveSession(record)
        persistGuardData(
            accountName = accountName,
            steamId = record.steamId,
            guardData = record.guardData,
        )
        return record
    }

    suspend fun getKnownGuardData(
        accountName: String? = null,
        steamId: String? = null,
    ): String? {
        return steamGuardDataRepository.getGuardData(accountName = accountName, steamId = steamId)
    }

    private suspend fun resolveGuardData(request: SteamProtocolLoginRequest): String? {
        val explicitGuardData = request.guardData?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitGuardData != null) {
            return explicitGuardData
        }

        val snapshotGuardData = request.existingAccount?.session?.guardData
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (snapshotGuardData != null) {
            return snapshotGuardData
        }

        val accountName = request.existingAccount?.accountName ?: request.username
        return steamGuardDataRepository.getGuardData(
            accountName = accountName,
            steamId = request.existingAccount?.steamId,
        )
    }

    private suspend fun persistGuardData(
        accountName: String?,
        steamId: String?,
        guardData: String?,
    ) {
        val normalizedGuardData = guardData?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val normalizedAccountName = accountName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedSteamId = steamId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedAccountName == null && normalizedSteamId == null) {
            return
        }

        steamGuardDataRepository.saveGuardData(
            SteamGuardDataRecord(
                accountName = normalizedAccountName,
                steamId = normalizedSteamId,
                guardData = normalizedGuardData,
                updatedAt = Instant.now().toString(),
            ),
        )
    }
}
