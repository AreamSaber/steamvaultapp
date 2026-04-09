package com.example.steam_vault_app.domain.sync

import com.example.steam_vault_app.domain.model.SteamQrLoginAuthSessionInfo
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.TokenRecord

interface SteamQrLoginApprovalManager {
    suspend fun inspectAuthSession(
        token: TokenRecord,
        session: SteamSessionRecord,
        challengeUrl: String,
    ): SteamQrLoginAuthSessionInfo

    suspend fun resolveAuthSession(
        token: TokenRecord,
        session: SteamSessionRecord,
        sessionInfo: SteamQrLoginAuthSessionInfo,
        approve: Boolean,
    )
}
