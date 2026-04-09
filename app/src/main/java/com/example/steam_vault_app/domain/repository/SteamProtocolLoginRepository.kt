package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge.QrCode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.model.SteamProtocolLoginResult

interface SteamProtocolLoginRepository {
    suspend fun login(
        request: SteamProtocolLoginRequest,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
        onQrChallengeChanged: suspend (QrCode) -> Unit = {},
    ): SteamProtocolLoginResult

    suspend fun refreshAccessToken(
        session: SteamMobileSession,
        allowRefreshTokenRenewal: Boolean = false,
    ): SteamMobileSession
}
