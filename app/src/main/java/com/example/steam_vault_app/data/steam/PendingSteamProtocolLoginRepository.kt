package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge.QrCode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.model.SteamProtocolLoginResult
import com.example.steam_vault_app.domain.repository.SteamProtocolLoginRepository

class PendingSteamProtocolLoginRepository : SteamProtocolLoginRepository {
    override suspend fun login(
        request: SteamProtocolLoginRequest,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
        onQrChallengeChanged: suspend (QrCode) -> Unit,
    ): SteamProtocolLoginResult {
        throw UnsupportedOperationException(
            "Steam protocol login is not implemented yet. The next step is porting the SteamKit/SDA login state machine.",
        )
    }

    override suspend fun refreshAccessToken(
        session: SteamMobileSession,
        allowRefreshTokenRenewal: Boolean,
    ): SteamMobileSession {
        throw UnsupportedOperationException(
            "Steam protocol refresh is not implemented yet. The next step is porting the SteamKit/SDA refresh flow.",
        )
    }
}
