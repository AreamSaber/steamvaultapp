package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressDraft

interface SteamAuthenticatorBindingProgressRepository {
    suspend fun getProgress(): SteamAuthenticatorBindingProgressDraft?

    suspend fun saveProgress(progress: SteamAuthenticatorBindingProgressDraft)

    suspend fun clearProgress()
}
