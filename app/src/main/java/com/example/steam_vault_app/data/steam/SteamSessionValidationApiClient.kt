package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamSessionCookie

internal data class SteamSessionValidationRequest(
    val cookies: List<SteamSessionCookie>,
    val steamIdHint: String?,
)

internal data class SteamSessionValidationResult(
    val resolvedSteamId: String?,
    val profileUrl: String,
)

internal interface SteamSessionValidationApiClient {
    suspend fun validateSession(request: SteamSessionValidationRequest): SteamSessionValidationResult
}
