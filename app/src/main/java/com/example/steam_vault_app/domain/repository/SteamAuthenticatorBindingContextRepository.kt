package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext

interface SteamAuthenticatorBindingContextRepository {
    suspend fun getContext(): SteamAuthenticatorBindingContext?

    suspend fun saveContext(context: SteamAuthenticatorBindingContext)

    suspend fun clearContext()
}
