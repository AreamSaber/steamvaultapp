package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamConfirmation
import com.example.steam_vault_app.domain.model.SteamSessionCookie

internal data class SteamConfirmationRequest(
    val steamId: String,
    val deviceId: String,
    val cookies: List<SteamSessionCookie>,
    val timestampSeconds: Long,
    val confirmationKey: String,
    val tag: String,
)

internal interface SteamConfirmationApiClient {
    suspend fun fetchConfirmations(request: SteamConfirmationRequest): List<SteamConfirmation>

    suspend fun resolveConfirmation(
        request: SteamConfirmationRequest,
        confirmationId: String,
        confirmationNonce: String,
        approve: Boolean,
    )
}
