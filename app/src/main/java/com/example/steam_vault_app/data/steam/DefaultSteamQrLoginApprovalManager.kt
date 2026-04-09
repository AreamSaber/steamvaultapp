package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamQrLoginAuthSessionInfo
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.sync.SteamQrLoginApprovalManager

internal class DefaultSteamQrLoginApprovalManager(
    private val steamWebApiClient: OkHttpSteamWebApiClient = OkHttpSteamWebApiClient(),
) : SteamQrLoginApprovalManager {
    override suspend fun inspectAuthSession(
        token: TokenRecord,
        session: SteamSessionRecord,
        challengeUrl: String,
    ): SteamQrLoginAuthSessionInfo {
        val accessToken = resolveAccessToken(session)
        val clientId = SteamQrLoginChallengeParser.resolveClientId(challengeUrl)
        val response = steamWebApiClient.getAuthSessionInfo(
            clientId = clientId,
            accessToken = accessToken,
        )
        val version = response.version
            ?: throw IllegalStateException("Steam QR login session did not return a version.")
        return SteamQrLoginAuthSessionInfo(
            challengeUrl = challengeUrl.trim(),
            clientId = clientId,
            version = version,
            ip = response.ip,
            geoloc = response.geoloc,
            city = response.city,
            state = response.state,
            country = response.country,
            platformType = response.platformType,
            deviceFriendlyName = response.deviceFriendlyName,
            loginHistory = response.loginHistory,
            requestorLocationMismatch = response.requestorLocationMismatch,
            highUsageLogin = response.highUsageLogin,
            requestedPersistence = response.requestedPersistence,
            deviceTrust = response.deviceTrust,
            appType = response.appType,
        )
    }

    override suspend fun resolveAuthSession(
        token: TokenRecord,
        session: SteamSessionRecord,
        sessionInfo: SteamQrLoginAuthSessionInfo,
        approve: Boolean,
    ) {
        val accessToken = resolveAccessToken(session)
        val steamId = session.steamId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toLongOrNull()
            ?: throw IllegalStateException(
                "SteamID is required before approving QR login requests.",
            )
        val signature = SteamQrLoginSigner.generateSignature(
            sharedSecret = token.sharedSecret,
            version = sessionInfo.version,
            clientId = sessionInfo.clientId,
            steamId = steamId,
        )
        steamWebApiClient.updateAuthSessionWithMobileConfirmation(
            request = UpdateAuthSessionWithMobileConfirmationRequest(
                version = sessionInfo.version,
                clientId = sessionInfo.clientId,
                steamId = steamId,
                signature = signature,
                confirm = approve,
                persistence = sessionInfo.requestedPersistence
                    ?: SteamAuthSessionPersistence.PERSISTENT,
            ),
            accessToken = accessToken,
        )
    }

    private fun resolveAccessToken(session: SteamSessionRecord): String {
        return session.accessToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: session.oauthToken
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException(
                "Steam access token is required before approving QR login requests.",
            )
    }
}
